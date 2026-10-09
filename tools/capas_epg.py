import json, os, re, unicodedata, urllib.request, collections, difflib, gzip
import xml.etree.ElementTree as ET

HOST = "http://HOST_DEL_PROVEEDOR"
USER = "TU_USUARIO"
PASS = "TU_CONTRASENA"
UA = "VLC/3.0.20"
DIR = os.path.expanduser("~/iptv")
MAX_CAPAS = 5

# Variante que dobleM recomienda para TiviMate (con caracteres especiales, año | edad | valoración en el título)
EPG_URL = "https://raw.githubusercontent.com/davidmuma/EPG_dobleM/master/guiatv_sincolor.xml.gz"

PAQUETES = [
    {"nombre": "M+",       "grupos": lambda g: "MOVISTAR" in g or g.startswith("ES| M+")},
    {"nombre": "Vodafone", "grupos": lambda g: "VODAFONE" in g},
    {"nombre": "Orange",   "grupos": lambda g: "ORANGE" in g},
]

PREFERENCIA = ["RAW", "UHD", "FHD", "HD", "HEVC", "SD", "LOW"]
ETIQUETAS = (r"\b(UHD|FHD|HD|SD|RAW|HEVC|LOW|4K|8K|ULTRA|HDR|60FPS|VIP|H265|H264|BK|"
             r"3840P|2160P|1080P|720P)\b")

# Nombres del proveedor que la guía escribe de otra forma
SINONIMOS = [("LCAMPEONES", "LIGA DE CAMPEONES"), ("LA LIGA", "LALIGA"),
             ("DISNEY JR", "DISNEY JUNIOR"), ("NAT GEOGRAPHIC", "NATIONAL GEOGRAPHIC"),
             ("CLAN TVE", "CLAN"), ("DISCOVERY CHANEL", "DISCOVERY"),
             ("DISCOVERY CHANNEL", "DISCOVERY"), ("HOLLYWOOD", "CANAL HOLLYWOOD")]

EXACTOS = {"VAMOS": "M+ VAMOS", "M+ DEPORTES 1": "M+ DEPORTES", "M+ CINE": "M+ CINE ESPANOL"}

def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    return urllib.request.urlopen(req, timeout=120)

def api(action):
    return json.load(get(f"{HOST}/player_api.php?username={USER}&password={PASS}&action={action}"))

def limpio(n):
    n = unicodedata.normalize("NFKD", n)
    return "".join(c for c in n if not unicodedata.combining(c)).upper()

def clave(nombre):
    n = re.sub(r"\(.*?\)", " ", limpio(nombre))
    n = re.sub(r"^\s*[A-Z0-9+]{1,3}\s*:\s*", "", n)
    n = re.sub(ETIQUETAS, " ", n)
    n = re.sub(r"[^\w+ ]", " ", n)
    n = re.sub(r"\s+", " ", n).strip()
    return re.sub(r"^(MOVISTAR PLUS\+?|MOVISTAR\+?|M\+?) ", "M+ ", n)

def puntos(nombre):
    n = limpio(nombre)
    hallados = [i for i, t in enumerate(PREFERENCIA) if re.search(rf"\b{t}\b", n)]
    p = max(hallados) if hallados else PREFERENCIA.index("HD")
    if "ULTRA" in n: p = min(p, 0)
    if "HDR" in n or "SOLO EVENTOS" in n: p += 10
    return p

# ---------- EPG: canales, orden e iconos ----------
def cargar_epg():
    cache = f"{DIR}/epg_canales.json"
    canales = []
    try:
        flujo = get(EPG_URL)
        if EPG_URL.endswith(".gz"):
            flujo = gzip.GzipFile(fileobj=flujo)
        for ev, el in ET.iterparse(flujo, events=("start", "end")):
            if ev == "start" and el.tag == "programme":
                break                                  # solo hace falta la cabecera
            if ev == "end" and el.tag == "channel":
                ic = el.find("icon")
                canales.append([el.get("id"), [d.text or "" for d in el.findall("display-name")],
                                ic.get("src") if ic is not None else ""])
                el.clear()
        json.dump(canales, open(cache, "w", encoding="utf-8"), ensure_ascii=False)
    except Exception as e:
        print("No se pudo bajar la guía, uso la copia guardada:", e)
        canales = json.load(open(cache, encoding="utf-8"))
    return canales

def compacto(k):
    return k.replace(" ", "")

def indice_epg(epg):
    idx = {}
    for pos, (cid, nombres, icono) in enumerate(epg):
        for n in nombres:
            k = clave(n)
            if k: idx.setdefault(k, pos)
    return idx

def cargar_alias():
    alias = {}
    f = f"{DIR}/alias_epg.txt"
    if os.path.exists(f):
        for l in open(f, encoding="utf-8"):
            if "=" in l and not l.strip().startswith("#"):
                a, b = l.split("=", 1)
                alias[clave(a)] = clave(b)
    return alias

def buscar_epg(k, idx, comp, alias):
    """Devuelve la posición del canal en la guía o None."""
    if k in alias and alias[k] in idx: return idx[alias[k]]
    if k in idx: return idx[k]
    k2 = EXACTOS.get(k, k)
    for a, b in SINONIMOS:
        k2 = re.sub(rf"\b{re.escape(a)}\b", b, k2)
    if k2 in idx: return idx[k2]
    c = compacto(k2)
    if c in comp: return comp[c]
    for m in difflib.get_close_matches(c, list(comp), n=3, cutoff=0.88):
        if re.findall(r"\d+", m) == re.findall(r"\d+", c):   # los números deben coincidir
            return comp[m]
    return None

def main():
    os.makedirs(DIR, exist_ok=True)
    epg = cargar_epg()
    idx = indice_epg(epg)
    comp = {compacto(k): p for k, p in idx.items()}
    alias = cargar_alias()

    cats = {c["category_id"]: c["category_name"] for c in api("get_live_categories")}
    live = api("get_live_streams")
    out = [f'#EXTM3U url-tvg="{EPG_URL}" x-tvg-url="{EPG_URL}"']
    sin_epg = []

    for pq in PAQUETES:
        nom = pq["nombre"]
        canales = collections.OrderedDict()
        for s in live:
            g = cats.get(s.get("category_id"), "").upper()
            if not g.startswith("ES|") or not pq["grupos"](g) or s["name"].strip().startswith("#"):
                continue
            canales.setdefault(clave(s["name"]), []).append(s)

        pos = {k: buscar_epg(k, idx, comp, alias) for k in canales}
        con = [k for k in canales if pos[k] is not None]
        sin = [k for k in canales if pos[k] is None]
        orden = sorted(con, key=lambda k: pos[k]) + sin      # orden de la guía; el resto al final
        sin_epg += [f"{nom}: {k}" for k in sin]

        cuenta = collections.Counter()
        for capa in range(MAX_CAPAS):
            for k in orden:
                v = sorted(canales[k], key=lambda s: (puntos(s["name"]), s["num"]))
                if capa >= len(v): continue
                s = v[capa]
                if pos[k] is not None:
                    cid, _, icono = epg[pos[k]]
                else:
                    cid, icono = "", s.get("stream_icon") or ""
                out.append(f'#EXTINF:-1 tvg-id="{cid}" tvg-name="{k}" tvg-logo="{icono}" '
                           f'group-title="{nom} {capa+1}",{k}')
                out.append(f'{HOST}/live/{USER}/{PASS}/{s["stream_id"]}.ts')
                cuenta[capa + 1] += 1
        print(f"{nom}: {len(orden)} canales | con guía: {len(con)} | sin guía: {len(sin)}")
        print("   " + "  ".join(f"capa {c}: {cuenta[c]}" for c in sorted(cuenta)))

    open(f"{DIR}/lista_capas.m3u", "w", encoding="utf-8").write("\n".join(out) + "\n")
    open(f"{DIR}/sin_epg.txt", "w", encoding="utf-8").write("\n".join(sin_epg) + "\n")
    print(f"\nLista: {DIR}/lista_capas.m3u\nCanales sin guía: {DIR}/sin_epg.txt")

if __name__ == "__main__":
    main()
