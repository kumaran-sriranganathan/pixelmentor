"""
populate_search_index.py
------------------------
Run once (or in CI) to create the photography-lessons index and upload all lesson documents.

Usage:
    python scripts/populate_search_index.py

Environment variables (same ones injected into Container App via Terraform):
    AZURE_SEARCH_ENDPOINT   e.g. https://pm-dev-search.search.windows.net
    AZURE_SEARCH_KEY        Admin key
    SEARCH_INDEX_NAME       photography-lessons  (default)
"""

import json
import os
import sys

from azure.core.credentials import AzureKeyCredential
from azure.search.documents import SearchClient
from azure.search.documents.indexes import SearchIndexClient
from azure.search.documents.indexes.models import (
    SearchIndex,
    SearchField,
    SearchFieldDataType,
    SimpleField,
    SearchableField,
    VectorSearch,
    HnswAlgorithmConfiguration,
    VectorSearchProfile,
)

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------
ENDPOINT = os.environ["AZURE_SEARCH_ENDPOINT"]
KEY = os.environ["AZURE_SEARCH_KEY"]
INDEX_NAME = os.environ.get("SEARCH_INDEX_NAME", "photography-lessons")

CREDENTIAL = AzureKeyCredential(KEY)

# ---------------------------------------------------------------------------
# Index schema
# ---------------------------------------------------------------------------
def get_index() -> SearchIndex:
    fields = [
        SimpleField(name="id", type=SearchFieldDataType.String, key=True, filterable=True),
        SearchableField(name="title", type=SearchFieldDataType.String, analyzer_name="en.microsoft"),
        SearchableField(name="description", type=SearchFieldDataType.String, analyzer_name="en.microsoft"),
        SearchableField(name="content", type=SearchFieldDataType.String, analyzer_name="en.microsoft"),
        SimpleField(name="category", type=SearchFieldDataType.String, filterable=True, facetable=True),
        SimpleField(name="difficulty", type=SearchFieldDataType.String, filterable=True, facetable=True),
        SimpleField(name="duration_minutes", type=SearchFieldDataType.Int32, filterable=True, sortable=True),
        SimpleField(name="is_pro", type=SearchFieldDataType.Boolean, filterable=True),
        SimpleField(name="order", type=SearchFieldDataType.Int32, sortable=True),
        SimpleField(
            name="tags",
            type=SearchFieldDataType.Collection(SearchFieldDataType.String),
            filterable=True,
            facetable=True,
        ),
    ]
    return SearchIndex(name=INDEX_NAME, fields=fields)


# ---------------------------------------------------------------------------
# Lesson data
# ---------------------------------------------------------------------------
LESSONS = [
    # ── Beginner / Fundamentals ────────────────────────────────────────────
    {
        "id": "lesson-001",
        "title": "Understanding Your Camera",
        "description": "Get comfortable with your camera body, buttons, and menus.",
        "content": (
            "In this lesson you will learn the physical layout of a DSLR or mirrorless camera. "
            "We cover the shutter button, mode dial, exposure compensation, ISO button, and the "
            "importance of reading your histogram. By the end you will know how to navigate the "
            "most common menus and customise your function buttons for faster shooting."
        ),
        "category": "fundamentals",
        "difficulty": "beginner",
        "duration_minutes": 15,
        "is_pro": False,
        "order": 1,
        "tags": ["camera", "beginner", "gear"],
    },
    {
        "id": "lesson-002",
        "title": "The Exposure Triangle",
        "description": "Master aperture, shutter speed, and ISO — the three pillars of exposure.",
        "content": (
            "Aperture controls depth of field and light intake (f/1.8 is wide; f/16 is narrow). "
            "Shutter speed freezes or blurs motion (1/1000s freezes; 1/30s blurs). "
            "ISO amplifies the sensor signal but adds noise above ISO 3200 on most cameras. "
            "This lesson uses interactive examples so you can see the effect of each variable "
            "in isolation before combining them in Manual mode."
        ),
        "category": "fundamentals",
        "difficulty": "beginner",
        "duration_minutes": 20,
        "is_pro": False,
        "order": 2,
        "tags": ["exposure", "aperture", "shutter-speed", "iso", "beginner"],
    },
    {
        "id": "lesson-003",
        "title": "Composition Rules Every Photographer Should Know",
        "description": "Rule of thirds, leading lines, framing, and more.",
        "content": (
            "Good composition turns a snapshot into a photograph. We start with the rule of thirds "
            "grid overlay, then explore leading lines that guide the viewer's eye, natural frames "
            "(doorways, branches), foreground interest for depth, and the power of negative space. "
            "Practical exercises use street and landscape examples."
        ),
        "category": "composition",
        "difficulty": "beginner",
        "duration_minutes": 25,
        "is_pro": False,
        "order": 3,
        "tags": ["composition", "rule-of-thirds", "leading-lines", "beginner"],
    },
    {
        "id": "lesson-004",
        "title": "Understanding Light: Quality, Direction & Colour",
        "description": "Learn why light is the most important ingredient in photography.",
        "content": (
            "Hard light (direct sun at noon) creates harsh shadows; soft light (overcast sky, shade) "
            "wraps around your subject. Direction — front, side, back — changes mood dramatically. "
            "Colour temperature shifts from the cool blue of shade (7000 K) to the warm gold of "
            "sunrise (3000 K). We show how to read and use natural light at any time of day."
        ),
        "category": "fundamentals",
        "difficulty": "beginner",
        "duration_minutes": 20,
        "is_pro": False,
        "order": 4,
        "tags": ["light", "colour-temperature", "natural-light", "beginner"],
    },
    # ── Intermediate ──────────────────────────────────────────────────────
    {
        "id": "lesson-005",
        "title": "Shooting in Manual Mode",
        "description": "Take full creative control with Manual (M) mode.",
        "content": (
            "Manual mode lets you set aperture, shutter speed, and ISO independently. "
            "We walk through a typical portrait scenario (f/2.8, 1/200s, ISO 400) and a landscape "
            "scenario (f/11, 1/30s, ISO 100 on a tripod). You will learn to use the in-camera "
            "exposure meter and when to override it intentionally for creative effect."
        ),
        "category": "fundamentals",
        "difficulty": "intermediate",
        "duration_minutes": 30,
        "is_pro": False,
        "order": 5,
        "tags": ["manual-mode", "exposure", "intermediate"],
    },
    {
        "id": "lesson-006",
        "title": "Portrait Photography: Light and Posing",
        "description": "Flatter your subjects with posing guidance and flattering light setups.",
        "content": (
            "This lesson covers the classic portrait lighting patterns: Rembrandt, loop, split, and "
            "butterfly. We then discuss posing — chin forward and down, body angle, hand placement — "
            "and how to give direction without making subjects feel awkward. Includes a checklist for "
            "your next portrait session."
        ),
        "category": "portrait",
        "difficulty": "intermediate",
        "duration_minutes": 35,
        "is_pro": False,
        "order": 6,
        "tags": ["portrait", "lighting", "posing", "intermediate"],
    },
    {
        "id": "lesson-007",
        "title": "Street Photography: Capturing Candid Moments",
        "description": "Ethics, technique, and gear for shooting in public.",
        "content": (
            "Street photography is about authentic human moments. We discuss the legal and ethical "
            "considerations of photographing strangers, then cover practical technique: zone focusing "
            "for speed, shooting from the hip, using reflections and shadows creatively, and how to "
            "be less conspicuous. Gear recommendations range from a compact to a mirrorless with "
            "a 35 mm prime."
        ),
        "category": "street",
        "difficulty": "intermediate",
        "duration_minutes": 30,
        "is_pro": False,
        "order": 7,
        "tags": ["street", "candid", "documentary", "intermediate"],
    },
    {
        "id": "lesson-008",
        "title": "Landscape Photography: Golden Hour and Beyond",
        "description": "Plan, shoot, and process stunning landscapes.",
        "content": (
            "Golden hour (the hour after sunrise and before sunset) offers warm, directional light "
            "and long shadows perfect for landscapes. We cover location scouting with apps like "
            "PhotoPills, tripod technique, hyperfocal distance for front-to-back sharpness, "
            "ND filters for long exposures, and a basic Lightroom processing workflow."
        ),
        "category": "landscape",
        "difficulty": "intermediate",
        "duration_minutes": 40,
        "is_pro": False,
        "order": 8,
        "tags": ["landscape", "golden-hour", "tripod", "ND-filter", "intermediate"],
    },
    # ── Pro / Advanced ─────────────────────────────────────────────────────
    {
        "id": "lesson-009",
        "title": "Advanced Flash Techniques: Off-Camera Lighting",
        "description": "Use speedlights and strobes to sculpt light like a pro.",
        "content": (
            "Moving your flash off-camera transforms your images. We cover radio triggers, "
            "bare flash versus softboxes/umbrellas, the inverse square law in practice, "
            "mixing flash with ambient light using high-speed sync, and a one-light portrait "
            "setup you can build for under $100. Bonus: outdoor fill flash to compete with the sun."
        ),
        "category": "lighting",
        "difficulty": "advanced",
        "duration_minutes": 50,
        "is_pro": True,
        "order": 9,
        "tags": ["flash", "off-camera-flash", "studio", "speedlight", "advanced"],
    },
    {
        "id": "lesson-010",
        "title": "Mastering Lightroom: Advanced Colour Grading",
        "description": "Give your images a cinematic, consistent look with colour science.",
        "content": (
            "We go beyond basic sliders into HSL (hue, saturation, luminance) adjustments, "
            "the Tone Curve, the Colour Mixer, and Camera Calibration panel. You will learn "
            "how to create and export presets for batch processing, match exposure across a "
            "shoot using the Reference Photo view, and develop a signature colour palette. "
            "Case studies: moody dark tones, airy bright tones, and film-simulation looks."
        ),
        "category": "post-processing",
        "difficulty": "advanced",
        "duration_minutes": 60,
        "is_pro": True,
        "order": 10,
        "tags": ["lightroom", "colour-grading", "post-processing", "advanced"],
    },
    {
        "id": "lesson-011",
        "title": "Wildlife Photography: Focus Tracking and Fieldcraft",
        "description": "Get sharp shots of fast-moving animals in the field.",
        "content": (
            "Wildlife photography demands fast autofocus, patience, and fieldcraft. We cover "
            "continuous AF modes (AF-C / AI Servo), subject recognition AF on modern cameras, "
            "burst shooting strategy, burst buffer management, understanding animal behaviour "
            "to anticipate the peak moment, and camouflage/hide techniques to get closer without "
            "disturbing wildlife."
        ),
        "category": "wildlife",
        "difficulty": "advanced",
        "duration_minutes": 55,
        "is_pro": True,
        "order": 11,
        "tags": ["wildlife", "autofocus", "animals", "advanced"],
    },
    {
        "id": "lesson-012",
        "title": "Building Your Photography Business",
        "description": "Pricing, marketing, contracts, and client management.",
        "content": (
            "Turning passion into profit requires business skills. We cover pricing your work "
            "(cost-plus vs market-rate vs value-based pricing), building a portfolio website, "
            "social media strategy for photographers, writing contracts that protect you, "
            "licensing vs work-for-hire, and how to handle difficult clients professionally."
        ),
        "category": "business",
        "difficulty": "advanced",
        "duration_minutes": 45,
        "is_pro": True,
        "order": 12,
        "tags": ["business", "pricing", "contracts", "marketing", "advanced"],
    },
]


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
def create_or_update_index(index_client: SearchIndexClient) -> None:
    index = get_index()
    result = index_client.create_or_update_index(index)
    print(f"✅ Index '{result.name}' created/updated with {len(result.fields)} fields.")


def upload_lessons(search_client: SearchClient) -> None:
    result = search_client.upload_documents(documents=LESSONS)
    succeeded = sum(1 for r in result if r.succeeded)
    failed = len(result) - succeeded
    print(f"✅ Uploaded {succeeded} lesson(s). Failed: {failed}.")
    if failed:
        for r in result:
            if not r.succeeded:
                print(f"  ❌ {r.key}: {r.error_message}")
        sys.exit(1)


def main() -> None:
    index_client = SearchIndexClient(endpoint=ENDPOINT, credential=CREDENTIAL)
    create_or_update_index(index_client)

    search_client = SearchClient(endpoint=ENDPOINT, index_name=INDEX_NAME, credential=CREDENTIAL)
    upload_lessons(search_client)

    print("\n🎉 Done. Run the FastAPI app and query /lessons to verify.")


if __name__ == "__main__":
    main()
