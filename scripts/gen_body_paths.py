"""Converts MuscleMap's Swift path tables (MIT, Melih Colpan) into a Kotlin source file."""
import re

import sys

# Usage: python scripts/gen_body_paths.py <MuscleMap checkout> [output .kt]
base = sys.argv[1] + '/Sources/MuscleMap/Data'
out_path = sys.argv[2] if len(sys.argv) > 2 else 'app/src/main/java/com/wegojim/android/ui/body/BodyPaths.kt'

SLUGS = {  # Swift case -> raw slug
    'lowerBack': 'lower-back', 'upperBack': 'upper-back', 'rotatorCuff': 'rotator-cuff', 'hipFlexors': 'hip-flexors',
    'upperChest': 'upper-chest', 'lowerChest': 'lower-chest', 'innerQuad': 'inner-quad', 'outerQuad': 'outer-quad',
    'upperAbs': 'upper-abs', 'lowerAbs': 'lower-abs', 'frontDeltoid': 'front-deltoid', 'rearDeltoid': 'rear-deltoid',
    'upperTrapezius': 'upper-trapezius', 'lowerTrapezius': 'lower-trapezius',
}


def parse(name):
    src = open(f'{base}\\{name}.swift', encoding='utf8').read()
    parts = []
    for block in re.split(r'BodyPartPathData\(', src)[1:]:
        slug = re.search(r'slug:\s*\.(\w+)', block).group(1)
        entry = {'slug': SLUGS.get(slug, slug)}
        for key in ('common', 'left', 'right'):
            m = re.search(key + r':\s*\[(.*?)\]', block, re.S)
            entry[key] = re.findall(r'"([^"]+)"', m.group(1)) if m else []
        parts.append(entry)
    return parts


def kt_list(items):
    if not items:
        return 'emptyList()'
    return 'listOf(\n' + ''.join(f'                "{p}",\n' for p in items) + '            )'


def emit(var, parts):
    body = ''.join(
        f'        BodyPart(\n            "{p["slug"]}",\n            common = {kt_list(p["common"])},\n'
        f'            left = {kt_list(p["left"])},\n            right = {kt_list(p["right"])},\n        ),\n'
        for p in parts
    )
    return f'    val {var}: List<BodyPart> by lazy {{\n        listOf(\n{body}        )\n    }}\n'


front, back = parse('MaleFrontPaths'), parse('MaleBackPaths')
header = '''// Generated from MuscleMap 1.6.4 (https://github.com/melihcolpan/MuscleMap).
// Copyright (c) 2026 Melih Colpan. MIT License; see licenses/MuscleMap-LICENSE.
// Do not edit by hand; regenerate with scripts/gen_body_paths.py.
@file:Suppress("LargeClass")

package com.wegojim.android.ui.body

/** SVG path data for one body part; left/right are the two sides, common spans both. */
class BodyPart(val slug: String, val common: List<String>, val left: List<String>, val right: List<String>) {
    val all: List<String> get() = common + left + right
}

/** Male body outlines in SVG viewBox coordinates. */
object BodyPaths {
    // viewBox origin and size, as in MuscleMap's BodyViewBox.
    const val FRONT_X = 0f
    const val BACK_X = 718f
    const val Y = 95f
    const val WIDTH = 727f
    const val HEIGHT = 1280f

'''
text = header + emit('front', front) + '\n' + emit('back', back) + '}\n'
import os
os.makedirs(os.path.dirname(out_path), exist_ok=True)
open(out_path, 'w', encoding='utf8').write(text)
print(len(front), len(back), 'parts;', sum(len(p['common']) + len(p['left']) + len(p['right']) for p in front + back), 'paths;', len(text) // 1024, 'KB')
print(sorted({p['slug'] for p in front + back}))
