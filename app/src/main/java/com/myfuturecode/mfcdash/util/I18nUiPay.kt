package com.myfuturecode.mfcdash.util

/** Payments list + app detail (ui/screens/PaymentsScreen.kt): English -> Hinglish. Missing key = English. */
val HI_UI_PAY: Map<String, String> = mapOf(
    // list + detail
    "All %s" to "Sab %s", "Paid %s" to "Paid %s", "Failed %s" to "Fail %s", "started" to "shuru",
    "Show more" to "Aur dikhao", "List from %s" to "List %s se", "Newest %s" to "Sabse naye %s",
    // server par payments.php nahi
    "Payments is not on the server yet" to "Payments abhi server par nahi hai",
    "Upload server/api/payments.php into mfc_dash/api/" to "server/api/payments.php ko mfc_dash/api/ me upload karo",
    // fail hone ki wajah (server payments.php ke shabd)
    "Cancelled" to "Cancel kiya", "No network" to "Net nahi tha", "Card declined" to "Card decline hua", "Billing unavailable" to "Billing band hai",
    "Item unavailable" to "Item nahi mila", "Developer error" to "Developer error", "Play error" to "Play error", "Already owned" to "Pehle se kharida",
    "Not owned" to "Kharida nahi", "Play disconnected" to "Play se connection toota", "Not supported" to "Support nahi", "Play timeout" to "Play timeout",
    "Payment pending" to "Payment baaki",
)
