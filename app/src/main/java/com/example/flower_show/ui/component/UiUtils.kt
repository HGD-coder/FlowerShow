package com.example.flower_show.ui.component

/**
 * Shared UI utilities — extracted from duplicated code across cards.
 * 共享 UI 工具——从卡片组件中提取的重复代码。
 */

/** Format count: 125000 → "12.5万", 8900 → "8900" */
fun formatCount(n: Int): String =
    if (n >= 10000) "${n / 10000}.${(n % 10000) / 1000}万" else n.toString()
