package com.nendo.argosy.ui.dualscreen.home

import com.nendo.argosy.ui.components.ActiveFilterChipUi
import com.nendo.argosy.ui.components.ActiveFilterKind

val DualFilterCategory.chipKind: ActiveFilterKind
    get() = when (this) {
        DualFilterCategory.SORT -> ActiveFilterKind.SORT
        DualFilterCategory.SEARCH -> ActiveFilterKind.SEARCH
        DualFilterCategory.SOURCE -> ActiveFilterKind.SOURCE
        DualFilterCategory.GENRE -> ActiveFilterKind.GENRE
        DualFilterCategory.PLAYERS -> ActiveFilterKind.PLAYERS
        DualFilterCategory.FRANCHISE -> ActiveFilterKind.SERIES
    }

val DualActiveFilters.chips: List<ActiveFilterChipUi>
    get() = entries.map { entry ->
        ActiveFilterChipUi(
            kind = entry.category.chipKind,
            labelRes = entry.labelRes,
            text = entry.text,
            count = entry.count
        )
    }
