package com.example.myapplication

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max

fun calculateDynamicPercentage(): Double {
    val today = LocalDate.now()
    val startOfYear = LocalDate.of(today.year, 1, 1)
    val endTargetDate = LocalDate.of(today.year, 11, 15)

    // നവംബർ 15 കഴിഞ്ഞാൽ ഫലം എപ്പോഴും 100% (1.0) ആയിരിക്കും
    if (today.isAfter(endTargetDate)) {
        return 1.0
    }

    // ദിവസങ്ങൾ തമ്മിലുള്ള വ്യത്യാസം കണ്ടുപിടിക്കുന്നു
    val daysPassed = ChronoUnit.DAYS.between(startOfYear, today).toDouble()
    val totalDays = ChronoUnit.DAYS.between(startOfYear, endTargetDate).toDouble()

    // എക്സൽ ഫോർമുല അതുപോലെ കോഡിലേക്ക് മാറ്റിയത്
    val elapsedRatio = daysPassed / totalDays
    val percentage = 2.0 - elapsedRatio

    // ഫലം 100%-ൽ കുറയാതിരിക്കാൻ
    return max(1.0, percentage)
}