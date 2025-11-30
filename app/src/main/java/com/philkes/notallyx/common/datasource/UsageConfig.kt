package com.philkes.notallyx.common.datasource

/**
 * Simplified placeholder for Starnest's UsageConfig and related classes.
 * NotallyX does not use these advanced usage limits; they are only here
 * so that the ported preferences code compiles.
 */
interface UsageConfig {
    var responseTimes: Int
    var requestTimes: Int
    var totalChatMessage: Int
    var isUserRated: Boolean
    var appOpenSession: Int
    var clickPremiumInNavTimes: Int
    var timeOpenApp: Int
}

enum class UsageKey {
    TOTAL_OPEN,
    TIME_FIRST_OPEN,
    NEXT_TIME_SHOW_RATING,
    CAN_RATE_US,
    TOTAL_SELECTED_HELP,
    LAST_REASON_SHOWED_RATING,
    CAN_SHOW_ANIMATION,
    USAGE_FREE_COPY,
    USAGE_TOTAL_LIMIT_HOURLY,
    USAGE_TOTAL_LIMIT_DAILY,
    LIMIT_TIME_HOURLY,
    LIMIT_TIME_DAILY,
    LIMIT_USED_CHAT_DAILY,
    LIMIT_USED_COPY_DAILY,
    CONFIG,
    CLICK_SEE_PREMIUM,
    DONT_SHOW_USAGE_ANYMORE
}

enum class AdvanceToolType {
    CHAT,
    COPY_PASTE
}