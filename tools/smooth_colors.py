"""Genera src/main/resources/assets/komaedit/smooth/colors.txt: color medio de textura de cada bloque vanilla.

Uso: python tools/smooth_colors.py <minecraft-client.jar>

El jar de cliente de la 1.21.1 está en la caché de Loom (~/.gradle/caches/fabric-loom/1.21.1/minecraft-client.jar).
Por cada bloque se lee su blockstate, el primer modelo y sus texturas (siguiendo los padres). El color es la media
de sus caras con textura distinta (arriba, abajo, lado…), ponderada por la opacidad de cada píxel. Las texturas
grises que Minecraft tiñe por bioma (césped, hojas…) se multiplican por el tinte de las llanuras.
"""
import io
import json
import sys
import zipfile
from pathlib import Path

from PIL import Image

FACE_KEYS = ("top", "bottom", "side", "all", "end", "texture", "wall", "front", "particle")
GRASS = (145, 189, 89)
FOLIAGE = (119, 171, 47)
TINTED = {
    "grass_block_top": GRASS,
    "short_grass": GRASS,
    "fern": GRASS,
    "oak_leaves": FOLIAGE,
    "jungle_leaves": FOLIAGE,
    "acacia_leaves": FOLIAGE,
    "dark_oak_leaves": FOLIAGE,
    "mangrove_leaves": (146, 194, 52),
    "spruce_leaves": (97, 153, 97),
    "birch_leaves": (128, 167, 85),
    "vine": FOLIAGE,
}
# Las caras de borde del bloque de césped son una superposición: se usa la tierra con su franja.
SKIP_TEXTURES = {"grass_block_side_overlay"}


def main(jar_path: str) -> None:
    jar = zipfile.ZipFile(jar_path)
    names = set(jar.namelist())

    def load_json(path):
        if path not in names:
            return None
        return json.loads(jar.read(path))

    def model_textures(model_id, depth=0):
        if depth > 16:
            return {}
        model_id = model_id.split(":")[-1]
        data = load_json(f"assets/minecraft/models/{model_id}.json")
        if data is None:
            return {}
        textures = {}
        if "parent" in data:
            textures.update(model_textures(data["parent"], depth + 1))
        textures.update(data.get("textures", {}))
        return textures

    def resolve(textures, value, depth=0):
        while isinstance(value, str) and value.startswith("#") and depth < 16:
            value = textures.get(value[1:])
            depth += 1
        return value

    cache = {}

    def texture_color(texture):
        name = texture.split(":")[-1]
        if name in cache:
            return cache[name]
        path = f"assets/minecraft/textures/{name}.png"
        if path not in names or name.split("/")[-1] in SKIP_TEXTURES:
            cache[name] = None
            return None
        image = Image.open(io.BytesIO(jar.read(path))).convert("RGBA")
        width, height = image.size
        # Texturas animadas: solo el primer fotograma.
        if height > width:
            image = image.crop((0, 0, width, width))
        red = green = blue = weight = 0.0
        for r, g, b, a in image.get_flattened_data() if hasattr(image, "get_flattened_data") else image.getdata():
            alpha = a / 255.0
            red += r * alpha
            green += g * alpha
            blue += b * alpha
            weight += alpha
        if weight < 1.0:
            cache[name] = None
            return None
        color = [red / weight, green / weight, blue / weight]
        tint = TINTED.get(name.split("/")[-1])
        if tint:
            color = [color[i] * tint[i] / 255.0 for i in range(3)]
        cache[name] = color
        return color

    def first_model(state):
        if "variants" in state:
            variant = next(iter(state["variants"].values()))
        elif "multipart" in state:
            variant = state["multipart"][0]["apply"]
        else:
            return None
        if isinstance(variant, list):
            variant = variant[0]
        return variant.get("model")

    rows = []
    for name in sorted(names):
        if not (name.startswith("assets/minecraft/blockstates/") and name.endswith(".json")):
            continue
        block = name.rsplit("/", 1)[1][:-5]
        model = first_model(load_json(name))
        if model is None:
            continue
        textures = model_textures(model)
        faces = []
        seen = set()
        for key in FACE_KEYS:
            value = resolve(textures, textures.get(key))
            if isinstance(value, str) and value not in seen:
                seen.add(value)
                faces.append(value)
        # La partícula solo cuenta si no hay otra cara (suele repetir el lado).
        particle = resolve(textures, textures.get("particle"))
        if len(faces) > 1 and particle == faces[-1] and particle not in faces[:-1]:
            faces.pop()
        colors = [color for color in (texture_color(face) for face in faces) if color is not None]
        if not colors:
            continue
        mean = [round(sum(color[i] for color in colors) / len(colors)) for i in range(3)]
        rows.append(f"minecraft:{block} {mean[0]} {mean[1]} {mean[2]}")

    output = Path(__file__).resolve().parent.parent / "src/main/resources/assets/komaedit/smooth/colors.txt"
    output.parent.mkdir(parents=True, exist_ok=True)
    header = ("# Color medio de textura (sRGB) por bloque vanilla 1.21.1, para elegir la losa, escalera, muro o capa\n"
              "# más parecida. Generado con tools/smooth_colors.py; no editar a mano.\n")
    output.write_text(header + "\n".join(rows) + "\n", encoding="utf-8", newline="\n")
    print(f"{len(rows)} bloques en {output}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
