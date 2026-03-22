def mask_token(value: object | None, *, keep_start: int = 4, keep_end: int = 4) -> str:
    """Return a log-friendly token preview without exposing the full value."""
    if value is None:
        return "-"

    token = str(value)
    if token == "":
        return "-"

    if keep_start < 0 or keep_end < 0:
        raise ValueError("mask_token keep lengths must be non-negative")

    if len(token) <= keep_start + keep_end:
        if len(token) <= 2:
            return "*" * len(token)
        return f"{token[0]}...{token[-1]}"

    return f"{token[:keep_start]}...{token[-keep_end:]}"
