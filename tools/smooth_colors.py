"""Genera src/main/resources/assets/komaedit/smooth/colors.txt: color medio de textura de cada bloque vanilla.

Uso: python tools/smooth_colors.py <minecraft-client.jar>

El jar de cliente de la 1.21.1 está en la caché de Loom (~/.gradle/caches/fabric-loom/1.21.1/minecraft-client.jar).
Por cada bloque se lee su blockstate, el primer modelo y sus texturas (siguiendo los padres). El color es la media
de sus caras con textura distinta (arriba, abajo, lado…), ponderada por la opacidad de cada píxel. Las texturas
grises que Minecraft tiñe por bioma (césped, hojas…) se multiplican por el tinte de las llanuras.

La quinta columna, opcional, lleva banderas para /komaedit color:
- «u»: el modelo es un cubo entero con la misma textura en las seis caras (piedra, lana, hormigón…; no los troncos,
  la arenisca ni el césped).
- «p»: no es un material liso: menas y bloques de mineral en bruto, bloques cincelados, coral vivo, terracota
  esmaltada, máquinas con cara delantera (horno, dispensador, observador…), algunos bloques de función (mesa de
  trabajo, librería, bloque musical…) y texturas con mucho contraste (desviación media en OKLab de más de 0,15 en
  alguna cara). Los degradados y el sombreado no los proponen salvo que se pida.
El resto de banderas (cubo entero, sólido, opaco, luz) se miran en el juego.
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
# Contraste (desviación media en OKLab) a partir del cual una textura tiene demasiado dibujo para un degradado.
BUSY_CONTRAST = 0.15
# Bloques de función que no se delatan por su textura.
FUNCTIONAL = {"chiseled_bookshelf", "note_block", "jukebox", "crafting_table", "fletching_table", "smithing_table",
              "cartography_table", "bookshelf", "tnt", "target", "respawn_anchor", "lodestone", "spawner",
              "trial_spawner", "vault", "sculk_sensor", "calibrated_sculk_sensor", "sculk_shrieker", "sculk_catalyst",
              "enchanting_table", "end_portal_frame", "beacon", "conduit", "crafter", "barrel", "composter",
              "redstone_lamp", "daylight_detector", "budding_amethyst", "reinforced_deepslate", "bedrock"}


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

    def model_elements(model_id, depth=0):
        if depth > 16:
            return None
        data = load_json(f"assets/minecraft/models/{model_id.split(':')[-1]}.json")
        if data is None:
            return None
        if "elements" in data:
            return data["elements"]
        return model_elements(data["parent"], depth + 1) if "parent" in data else None

    def uniform(model_id, textures):
        """Un solo elemento de 0 a 16 con sus seis caras y todas con la misma textura."""
        elements = model_elements(model_id)
        if not elements or len(elements) != 1:
            return False
        element = elements[0]
        if element.get("from") != [0, 0, 0] or element.get("to") != [16, 16, 16]:
            return False
        faces = element.get("faces", {})
        if set(faces) != {"north", "south", "east", "west", "up", "down"}:
            return False
        used = {resolve(textures, face.get("texture")) for face in faces.values()}
        return len(used) == 1 and isinstance(next(iter(used)), str)

    def resolve(textures, value, depth=0):
        while isinstance(value, str) and value.startswith("#") and depth < 16:
            value = textures.get(value[1:])
            depth += 1
        return value

    cache = {}
    contrasts = {}

    def oklab(red, green, blue):
        def linear(channel):
            channel /= 255.0
            return channel / 12.92 if channel <= 0.04045 else ((channel + 0.055) / 1.055) ** 2.4
        r, g, b = linear(red), linear(green), linear(blue)
        l_ = (0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b) ** (1 / 3)
        m_ = (0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b) ** (1 / 3)
        s_ = (0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b) ** (1 / 3)
        return (0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_,
                1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_,
                0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_)

    def contrast(texture):
        """Desviación media en OKLab de los píxeles opacos: cuánto dibujo tiene la textura."""
        name = texture.split(":")[-1]
        if name in contrasts:
            return contrasts[name]
        path = f"assets/minecraft/textures/{name}.png"
        value = 0.0
        if path in names:
            image = Image.open(io.BytesIO(jar.read(path))).convert("RGBA")
            width, height = image.size
            if height > width:
                image = image.crop((0, 0, width, width))
            pixels = image.get_flattened_data() if hasattr(image, "get_flattened_data") else image.getdata()
            labs = [oklab(r, g, b) for r, g, b, a in pixels if a > 128]
            if len(labs) >= 8:
                mean = [sum(lab[i] for lab in labs) / len(labs) for i in range(3)]
                value = (sum(sum((lab[i] - mean[i]) ** 2 for i in range(3)) for lab in labs) / len(labs)) ** 0.5
        contrasts[name] = value
        return value

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
        # Los bloques de coral vivos se mueren fuera del agua; los cincelados llevan un relieve.
        patterned = (block.endswith("_ore") or block.startswith("raw_") or "chiseled_" in block
                     or block.endswith("_coral_block") and not block.startswith("dead_")
                     or "glazed_terracotta" in block or "copper_bulb" in block
                     or block in FUNCTIONAL or "front" in textures
                     or max(contrast(face) for face in faces) > BUSY_CONTRAST)
        flags = ("u" if uniform(model, textures) else "") + ("p" if patterned else "")
        flags = " " + flags if flags else ""
        rows.append(f"minecraft:{block} {mean[0]} {mean[1]} {mean[2]}{flags}")

    output = Path(__file__).resolve().parent.parent / "src/main/resources/assets/komaedit/smooth/colors.txt"
    output.parent.mkdir(parents=True, exist_ok=True)
    header = ("# Color medio de textura (sRGB) por bloque vanilla 1.21.1, para elegir la losa, escalera, muro o capa\n"
              "# más parecida y para /komaedit color. Banderas: u = cubo con la misma textura en las seis caras;\n"
              "# p = no es un material liso (menas, esmaltada, máquinas, mucho dibujo).\n"
              "# Generado con tools/smooth_colors.py; no editar a mano.\n")
    output.write_text(header + "\n".join(rows) + "\n", encoding="utf-8", newline="\n")
    print(f"{len(rows)} bloques en {output}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
