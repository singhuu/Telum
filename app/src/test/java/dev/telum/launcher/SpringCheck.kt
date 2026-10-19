package dev.telum.launcher

/** A settled tile must stop requesting frames, even at large Float coordinates. */
fun main() {
    for (target in listOf(1f, 48f, 546f, 579f, 756f, 4096f, 20000f, -756f)) {
        for (dt in listOf(1f / 60f, 1f / 120f, 0.064f)) {
            val spring = Spring(0f, 300f, 0.86f)
            spring.target = target
            var frames = 0
            while (spring.step(dt) && frames < 2400) frames++
            check(spring.atRest && frames < 2400) {
                "Tile never settles: target=$target value=${spring.value} velocity=${spring.velocity} dt=$dt"
            }
            check(spring.value == target && spring.velocity == 0f)
            check(!spring.step(dt))
        }
    }
    val interrupted = Spring(0f, 300f, 0.86f)
    interrupted.target = 756f
    repeat(8) { interrupted.step(1f / 60f) }
    check(interrupted.value in 1f..755f && !interrupted.atRest)
    interrupted.target = 48f
    repeat(600) { interrupted.step(1f / 60f) }
    check(interrupted.value == 48f && !interrupted.step(1f / 60f))
    println("Spring checks passed: tile coordinates settle and stop rendering, including interrupted motion.")
}
