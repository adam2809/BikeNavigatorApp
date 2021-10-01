package com.example.bikenavigatorapp

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class RoundaboutTurnDirectionResolvingTest {

    fun getTestStep(
        startLat: Double, startLng: Double,
        endLat: Double, endLng: Double
    ) = Step(
        -1,
        TextVal("25 m", 25),
        TextVal("1 min", 4),
        "turn-right",
        Loc(startLat, startLng),
        Loc(endLat, endLng),
        "bla bla",
        "BICYCLING"
    )

    @Test
    fun shouldReturnLeftRoundaboutTurn() {
        val testBeforeAndAfterRoundaboutSteps = listOf(
            Pair(
                getTestStep(
                    52.1605765, 21.078864,
                    52.1604337, 21.078582
                ),
                getTestStep(
                    52.1604337, 21.078582,
                    52.1583714, 21.0804892
                )
            ),
            Pair(
                getTestStep(
                    52.222093, 21.024550,
                    52.219324, 21.025229
                ),
                getTestStep(
                    52.219324, 21.025229,
                    52.220552, 21.030152
                )
            ),
            Pair(
                getTestStep(
                    39.501013, -117.085876,
                    39.500152, -117.081241
                ),
                getTestStep(
                    39.500152, -117.081241,
                    39.503679, -117.080362
                )
            ),
            Pair(
                getTestStep(
                    -37.820585, 144.885214,
                    -37.820253, 144.882126
                ),
                getTestStep(
                    -37.820253, 144.882126,
                    -37.822240, 144.881658
                )
            ),
            Pair(
                getTestStep(
                    -37.815594, 144.750183,
                    -37.810392, 144.750745
                ),
                getTestStep(
                    -37.810392, 144.750745,
                    -37.809921, 144.744921
                )
            ),
            Pair(
                getTestStep(
                    -37.387531, -60.976190,
                    -37.379127, -60.986138
                ),
                getTestStep(
                    -37.379127, -60.986138,
                    -37.382612, -60.992032
                )
            ),
            Pair(
                getTestStep(
                    62.243057, 151.760626,
                    62.242872, 151.766120
                ),
                getTestStep(
                    62.242872, 151.766120,
                    62.245523, 151.766186
                )
            ),
            Pair(
                getTestStep(
                    -15.413711, 28.281226,
                    -15.408411, 28.280165
                ),
                getTestStep(
                    -15.408411, 28.280165,
                    -15.410953, 28.277497
                )
            ),
            Pair(
                getTestStep(
                    11.309450, 42.930657,
                    11.308833, 42.930399
                ),
                getTestStep(
                    11.308833, 42.930399,
                    11.308538, 42.931602
                )
            ),
            Pair(
                getTestStep(
                    -27.377512, -70.331270,
                    -27.378476, -70.332355
                ),
                getTestStep(
                    -27.378476, -70.332355,
                    -27.379983, -70.331186
                )
            )
        )

        val wrongReturnValue = testBeforeAndAfterRoundaboutSteps.filter { (before, after) ->
            resolveRoundaboutDirection(
                before,
                after
            ) != Dir.ROUNDABOUT_LEFT
        }
        assertTrue(wrongReturnValue.toString(), wrongReturnValue.isEmpty())
    }

    @Test
    fun shouldReturnRightRoundaboutTurn() {
        val testBeforeAndAfterRoundaboutSteps = listOf(
            Pair(
                getTestStep(
                    52.227937, 20.986520,
                    52.230369, 20.984283
                ),
                getTestStep(
                    52.230369, 20.984283,
                    52.231606, 20.991103
                )
            ),
            Pair(
                getTestStep(
                    -17.774483, -63.179049,
                    -17.774845, -63.182193
                ),
                getTestStep(
                    -17.774845, -63.182193,
                    -17.770737, -63.182434
                )
            ),
            Pair(
                getTestStep(
                    41.624867, -93.736457,
                    41.629193, -93.736457
                ),
                getTestStep(
                    41.629193, -93.736457,
                    41.629360, -93.728610
                )
            ),
            Pair(
                getTestStep(
                    -37.820585, 144.885214,
                    -37.820253, 144.882126
                ),
                getTestStep(
                    -37.820253, 144.882126,
                    -37.817658, 144.882536
                )
            ),
            Pair(
                getTestStep(
                    -37.815594, 144.750183,
                    -37.810392, 144.750745
                ),
                getTestStep(
                    -37.810392, 144.750745,
                    -37.810941, 144.756106
                )
            ),
            Pair(
                getTestStep(
                    -37.387531, -60.976190,
                    -37.379127, -60.986138
                ),
                getTestStep(
                    -37.379127, -60.986138,
                    -37.373507, -60.976709
                )
            ),
            Pair(
                getTestStep(
                    62.243057, 151.760626,
                    62.242872, 151.766120
                ),
                getTestStep(
                    62.242872, 151.766120,
                    62.240468, 151.766120
                )
            ),
            Pair(
                getTestStep(
                    -15.413711, 28.281226,
                    -15.408411, 28.280165
                ),
                getTestStep(
                    -15.408411, 28.280165,
                    -15.407326, 28.286788
                )
            ),
            Pair(
                getTestStep(
                    11.309450, 42.930657,
                    11.308833, 42.930399
                ),
                getTestStep(
                    11.308833, 42.930399,
                    11.309141, 42.929769
                )
            ),
            Pair(
                getTestStep(
                    -27.377512, -70.331270,
                    -27.378476, -70.332355
                ),
                getTestStep(
                    -27.378476, -70.332355,
                    -27.377166, -70.333357
                )
            ),
            Pair(
                getTestStep(
                    52.1585639, 21.0834342,
                    52.1579196, 21.0811308
                ),
                getTestStep(
                    52.1579196, 21.0811308,
                    52.1603162, 21.0785241
                )
            )
        )

        val wrongReturnValue = testBeforeAndAfterRoundaboutSteps.filter { (before, after) ->
            resolveRoundaboutDirection(
                before,
                after
            ) != Dir.ROUNDABOUT_RIGHT
        }
        assertTrue(wrongReturnValue.toString(), wrongReturnValue.isEmpty())
    }
}