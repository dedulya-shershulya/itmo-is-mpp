/**
 * @author Chkareuli Georgiy
 */
class Solution : MonotonicClock {
    private var c1d1 by RegularInt(0)
    private var c1d2 by RegularInt(0)
    private var c1d3 by RegularInt(0)

    private var c2d1 by RegularInt(0)
    private var c2d2 by RegularInt(0)
    private var c2d3 by RegularInt(0)

    override fun write(time: Time) {
        c2d1 = time.d1
        c2d2 = time.d2
        c2d3 = time.d3
        c1d3 = time.d3
        c1d2 = time.d2
        c1d1 = time.d1
    }

    override fun read(): Time {
        val lower = Time(c1d1, c1d2, c1d3)
        val upperD3 = c2d3
        val upperD2 = c2d2
        val upperD1 = c2d1
        val upper = Time(upperD1, upperD2, upperD3)

        return when {
            lower == upper -> lower
            lower.d1 != upper.d1 -> Time(lower.d1 + 1, 0, 0)
            lower.d2 != upper.d2 -> Time(lower.d1, lower.d2 + 1, 0)
            else -> Time(lower.d1, lower.d2, lower.d3 + 1)
        }
    }
}