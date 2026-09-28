package com.jpb.animator.utils

import kotlin.collections.toMutableList

fun <T> List<T>.swap(fromIndex: Int, toIndex: Int): List<T> {
    val list = this.toMutableList()
    val item = list.removeAt(fromIndex)
    list.add(toIndex, item)
    return list
}