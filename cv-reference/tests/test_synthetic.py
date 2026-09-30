from cv_reference.synthetic import Rect, SyntheticCardSpec

# Same fixture as Kotlin SyntheticCardTest.
SPEC = SyntheticCardSpec(800, 1000, card=Rect(85, 60, 715, 940), art=Rect(121, 100, 688, 900))


def test_ground_truth_borders():
    assert SPEC.borders == {"left": 36, "right": 27, "top": 40, "bottom": 40}


def test_ground_truth_centering_matches_kotlin():
    lr, tb = SPEC.centering()
    assert f"{lr:.1f}" == "57.1"
    assert f"{tb:.1f}" == "50.0"
