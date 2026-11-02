package com.cleanai.debloat.data

/**
 * Private mode: when on (the default), nothing leaves the phone. Every network helper in this
 * package checks [allowOnline] first, so a feature can't send data even by mistake.
 */
object Privacy {
    @Volatile var allowOnline = false

    fun blocked() = !allowOnline
}
