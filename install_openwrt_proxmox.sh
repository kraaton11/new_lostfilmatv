#!/usr/bin/env bash
set -euo pipefail

VMID=200
WAN_IP=192.168.2.200
WAN_GW=192.168.2.1
LAN_GW=10.10.10.1
LAN_NET=10.10.10.0/24

VM_NAME=openwrt-x86-64
WAN_BRIDGE=vmbr0
LAN_BRIDGE=vmbr1
STORAGE=${STORAGE:-local-lvm}
WORKDIR=${WORKDIR:-/var/lib/vz/template/cache/openwrt-x86-64}
OPENWRT_VERSION=${OPENWRT_VERSION:-25.12.4}
IMAGE_BASENAME=openwrt-${OPENWRT_VERSION}-x86-64-generic-ext4-combined.img
IMAGE_URL=https://downloads.openwrt.org/releases/${OPENWRT_VERSION}/targets/x86/64/${IMAGE_BASENAME}.gz
IMAGE_SHA256=9d080bcae28d7cdf86dabb4b29c10d36d89e0bd79e20a4799454380bc1619695

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

ensure_bridge_exists() {
  local bridge=$1
  ip link show "$bridge" >/dev/null 2>&1 || {
    echo "Required bridge $bridge does not exist. Refusing to modify host networking." >&2
    exit 1
  }
}

prefix_length_to_netmask() {
  local prefix=${1#*/}
  local mask=$(( 0xffffffff << (32 - prefix) & 0xffffffff ))
  printf '%d.%d.%d.%d\n' \
    $(( (mask >> 24) & 255 )) \
    $(( (mask >> 16) & 255 )) \
    $(( (mask >> 8) & 255 )) \
    $(( mask & 255 ))
}

write_openwrt_config() {
  local root_mount=$1
  local wan_netmask lan_netmask
  wan_netmask=$(prefix_length_to_netmask "${WAN_IP}/24")
  lan_netmask=$(prefix_length_to_netmask "$LAN_NET")

  cat > "${root_mount}/etc/config/network" <<EOF_NETWORK
config interface 'loopback'
	option device 'lo'
	option proto 'static'
	option ipaddr '127.0.0.1'
	option netmask '255.0.0.0'

config interface 'wan'
	option device 'eth0'
	option proto 'static'
	option ipaddr '${WAN_IP}'
	option netmask '${wan_netmask}'
	option gateway '${WAN_GW}'
	list dns '${WAN_GW}'

config interface 'lan'
	option device 'eth1'
	option proto 'static'
	option ipaddr '${LAN_GW}'
	option netmask '${lan_netmask}'
EOF_NETWORK

  cat > "${root_mount}/etc/config/dhcp" <<EOF_DHCP
config dnsmasq
	option domainneeded '1'
	option boguspriv '1'
	option filterwin2k '0'
	option localise_queries '1'
	option rebind_protection '1'
	option rebind_localhost '1'
	option local '/lan/'
	option domain 'lan'
	option expandhosts '1'
	option nonegcache '0'
	option cachesize '1000'
	option authoritative '1'
	option readethers '1'
	option leasefile '/tmp/dhcp.leases'
	option resolvfile '/tmp/resolv.conf.d/resolv.conf.auto'
	option nonwildcard '1'
	option localservice '1'
	option ednspacket_max '1232'

config dhcp 'lan'
	option interface 'lan'
	option start '100'
	option limit '101'
	option leasetime '12h'

config dhcp 'wan'
	option interface 'wan'
	option ignore '1'

config odhcpd 'odhcpd'
	option maindhcp '0'
	option leasefile '/tmp/hosts/odhcpd'
	option leasetrigger '/usr/sbin/odhcpd-update'
	option loglevel '4'
EOF_DHCP

  cat > "${root_mount}/etc/config/firewall" <<EOF_FIREWALL
config defaults
	option input 'REJECT'
	option output 'ACCEPT'
	option forward 'REJECT'
	option synflood_protect '1'

config zone
	option name 'lan'
	list network 'lan'
	option input 'ACCEPT'
	option output 'ACCEPT'
	option forward 'ACCEPT'

config zone
	option name 'wan'
	list network 'wan'
	option input 'REJECT'
	option output 'ACCEPT'
	option forward 'REJECT'
	option masq '1'
	option mtu_fix '1'

config forwarding
	option src 'lan'
	option dest 'wan'

config rule
	option name 'Allow-SSH-from-WAN'
	option src 'wan'
	option proto 'tcp'
	option dest_port '22'
	option target 'ACCEPT'

config rule
	option name 'Allow-LuCI-HTTP-from-WAN'
	option src 'wan'
	option proto 'tcp'
	option dest_port '80'
	option target 'ACCEPT'

config rule
	option name 'Allow-LuCI-HTTPS-from-WAN'
	option src 'wan'
	option proto 'tcp'
	option dest_port '443'
	option target 'ACCEPT'

config rule
	option name 'Allow-DHCP-Renew'
	option src 'wan'
	option proto 'udp'
	option dest_port '68'
	option target 'ACCEPT'
	option family 'ipv4'

config rule
	option name 'Allow-Ping'
	option src 'wan'
	option proto 'icmp'
	option icmp_type 'echo-request'
	option family 'ipv4'
	option target 'ACCEPT'

config rule
	option name 'Allow-192.168.2-to-LAN'
	option src 'wan'
	option dest 'lan'
	option src_ip '192.168.2.0/24'
	option dest_ip '${LAN_NET}'
	option proto 'all'
	option target 'ACCEPT'
EOF_FIREWALL

  cat > "${root_mount}/etc/config/dropbear" <<EOF_DROPBEAR
config dropbear
	option enable '1'
	option PasswordAuth 'on'
	option RootPasswordAuth 'on'
	option Port '22'
EOF_DROPBEAR
}

prepare_image() {
  mkdir -p "$WORKDIR"
  cd "$WORKDIR"

  if [ ! -f "${IMAGE_BASENAME}.gz" ]; then
    wget -O "${IMAGE_BASENAME}.gz" "$IMAGE_URL"
  fi

  echo "${IMAGE_SHA256}  ${IMAGE_BASENAME}.gz" | sha256sum -c -

  if [ ! -f "$IMAGE_BASENAME" ]; then
    gzip -dk "${IMAGE_BASENAME}.gz"
  fi

  local configured_marker="${IMAGE_BASENAME}.configured"
  if [ ! -f "$configured_marker" ]; then
    local loopdev mountdir
    loopdev=$(losetup -Pf --show "$IMAGE_BASENAME")
    mountdir=$(mktemp -d)
    cleanup_mount() {
      mountpoint -q "$mountdir" && umount "$mountdir"
      losetup -d "$loopdev" >/dev/null 2>&1 || true
      rmdir "$mountdir" >/dev/null 2>&1 || true
    }
    trap cleanup_mount RETURN
    mount "${loopdev}p2" "$mountdir"
    write_openwrt_config "$mountdir"
    sync
    cleanup_mount
    trap - RETURN
    touch "$configured_marker"
  fi

  if [ ! -f "${IMAGE_BASENAME}.qcow2" ]; then
    qemu-img convert -f raw -O qcow2 "$IMAGE_BASENAME" "${IMAGE_BASENAME}.qcow2"
  fi
}

create_or_update_vm() {
  if qm status "$VMID" >/dev/null 2>&1; then
    echo "VM $VMID already exists; leaving existing disk intact and enforcing CPU/RAM/NIC/console settings."
  else
    qm create "$VMID" \
      --name "$VM_NAME" \
      --memory 256 \
      --cores 1 \
      --cpu host \
      --ostype l26 \
      --serial0 socket \
      --vga serial0 \
      --net0 "virtio,bridge=${WAN_BRIDGE}" \
      --net1 "virtio,bridge=${LAN_BRIDGE}"

    qm importdisk "$VMID" "${WORKDIR}/${IMAGE_BASENAME}.qcow2" "$STORAGE" --format qcow2
    local imported_volume
    imported_volume=$(qm config "$VMID" | awk -F': ' '/^unused[0-9]+:/ {print $2; exit}')
    if [ -z "$imported_volume" ]; then
      echo "Could not find imported disk in VM config." >&2
      exit 1
    fi
    qm set "$VMID" --virtio0 "$imported_volume"
  fi

  qm set "$VMID" \
    --memory 256 \
    --cores 1 \
    --serial0 socket \
    --vga serial0 \
    --boot order=virtio0 \
    --net0 "virtio,bridge=${WAN_BRIDGE}" \
    --net1 "virtio,bridge=${LAN_BRIDGE}"
}

main() {
  require_cmd ip
  require_cmd qm
  require_cmd pvesm
  require_cmd wget
  require_cmd gzip
  require_cmd sha256sum
  require_cmd qemu-img
  require_cmd losetup
  require_cmd mount
  require_cmd umount

  ensure_bridge_exists "$WAN_BRIDGE"
  ensure_bridge_exists "$LAN_BRIDGE"
  pvesm status | awk 'NR > 1 {print $1}' | grep -Fx "$STORAGE" >/dev/null || {
    echo "Storage $STORAGE does not exist." >&2
    exit 1
  }

  prepare_image
  create_or_update_vm

  if qm status "$VMID" | grep -q 'status: running'; then
    qm reboot "$VMID" || true
  else
    qm start "$VMID"
  fi

  echo "Waiting for ${WAN_IP} to answer ping..."
  for _ in $(seq 1 60); do
    if ping -c 1 -W 1 "$WAN_IP" >/dev/null 2>&1; then
      echo "OpenWrt VM $VMID is reachable at $WAN_IP"
      exit 0
    fi
    sleep 2
  done

  echo "VM started, but $WAN_IP did not answer ping within 120 seconds." >&2
  echo "Use: qm terminal $VMID" >&2
  exit 1
}

main "$@"
