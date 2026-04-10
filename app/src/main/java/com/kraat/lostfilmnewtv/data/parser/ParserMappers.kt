package com.kraat.lostfilmnewtv.data.parser

import com.kraat.lostfilmnewtv.data.db.ReleaseDetailsEntity
import com.kraat.lostfilmnewtv.data.db.ReleaseSummaryEntity
import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary

fun List<ReleaseSummary>.toSummaryEntities(): List<ReleaseSummaryEntity> = map(ReleaseSummaryEntity::fromModel)

fun List<ReleaseSummaryEntity>.toSummaryModels(): List<ReleaseSummary> = map(ReleaseSummaryEntity::toModel)

fun ReleaseDetails.toEntity(): ReleaseDetailsEntity = ReleaseDetailsEntity.fromModel(this)
