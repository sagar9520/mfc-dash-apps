package com.myfuturecode.mfcdash.util

/** Sort button / menu / pill (ui/kit/KitSort.kt) aur list pages ke sort shabd: English -> Hinglish. Missing key = English. */
val HI_UI_SORT: Map<String, String> = mapOf(
    // menu ke direction buttons (number / naam / tareekh)
    "High → low" to "Zyada → kam", "Low → high" to "Kam → zyada",
    "A → Z" to "A → Z", "Z → A" to "Z → A",
    "New → old" to "Naya → purana", "Old → new" to "Purana → naya",
    // TalkBack: sort button / pill
    "Sort: %s, high to low" to "Sort: %s, zyada se kam", "Sort: %s, low to high" to "Sort: %s, kam se zyada",
    "Sort: %s, A to Z" to "Sort: %s, A se Z", "Sort: %s, Z to A" to "Sort: %s, Z se A",
    "Sort: %s, newest first" to "Sort: %s, naya pehle", "Sort: %s, oldest first" to "Sort: %s, purana pehle",
    "Change sort" to "Sort badlo",
    // Apps row: crash pill
    "%s crash" to "%s crash",
    // App page: Play Store pill
    "Play" to "Play", "Open in Play Store" to "Play Store me kholo",
)
