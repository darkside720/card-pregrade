"""Synthetic card fixtures with exactly known geometry.

Mirrors the Kotlin ``SyntheticCardSpec`` (android/core/cv/src/test/.../fixtures) so both
implementations can be checked against identical ground truth. No copyrighted imagery.
"""
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Rect:
    left: int
    top: int
    right: int  # exclusive
    bottom: int  # exclusive


@dataclass(frozen=True)
class SyntheticCardSpec:
    canvas_width: int
    canvas_height: int
    card: Rect
    art: Rect
    background_rgb: tuple[int, int, int] = (0x20, 0x20, 0x20)
    border_rgb: tuple[int, int, int] = (0xF2, 0xD1, 0x4A)
    art_rgb: tuple[int, int, int] = (0x3A, 0x6F, 0xB0)

    @property
    def borders(self) -> dict[str, int]:
        return {
            "left": self.art.left - self.card.left,
            "right": self.card.right - self.art.right,
            "top": self.art.top - self.card.top,
            "bottom": self.card.bottom - self.art.bottom,
        }

    def centering(self) -> tuple[float, float]:
        """Ground-truth L/R and T/B splits: percentage share of the left and top borders."""
        b = self.borders
        lr = 100.0 * b["left"] / (b["left"] + b["right"])
        tb = 100.0 * b["top"] / (b["top"] + b["bottom"])
        return lr, tb


def render(spec: SyntheticCardSpec):
    """Render the spec to an RGB numpy array (H, W, 3). Requires numpy."""
    import numpy as np  # local import: the spec is usable without numpy

    img = np.empty((spec.canvas_height, spec.canvas_width, 3), dtype=np.uint8)
    img[:, :] = spec.background_rgb
    c, a = spec.card, spec.art
    img[c.top:c.bottom, c.left:c.right] = spec.border_rgb
    img[a.top:a.bottom, a.left:a.right] = spec.art_rgb
    return img
