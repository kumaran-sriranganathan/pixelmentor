"""
scripts/populate_typesense.py
------------------------------
Creates the photography-lessons collection in Typesense and uploads all lessons.

Usage:
    python scripts/populate_typesense.py

Environment variables:
    TYPESENSE_HOST      e.g. xxx.a1.typesense.net
    TYPESENSE_API_KEY   Admin API key
"""

import os
import sys
import typesense

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------
HOST = os.environ["TYPESENSE_HOST"]
API_KEY = os.environ["TYPESENSE_API_KEY"]

client = typesense.Client({
    "nodes": [{"host": HOST, "port": "443", "protocol": "https"}],
    "api_key": API_KEY,
    "connection_timeout_seconds": 10,
})

# ---------------------------------------------------------------------------
# Collection schema
# ---------------------------------------------------------------------------
SCHEMA = {
    "name": "photography-lessons",
    "fields": [
        {"name": "id",               "type": "string"},
        {"name": "title",            "type": "string"},
        {"name": "description",      "type": "string"},
        {"name": "content",          "type": "string"},
        {"name": "category",         "type": "string", "facet": True},
        {"name": "difficulty",       "type": "string", "facet": True},
        {"name": "duration_minutes", "type": "int32"},
        {"name": "is_pro",           "type": "bool",   "facet": True},
        {"name": "order",            "type": "int32",  "sort": True},
        {"name": "tags",             "type": "string[]", "facet": True},
    ],
    "default_sorting_field": "order",
}

# ---------------------------------------------------------------------------
# Lesson data (same lessons as Azure AI Search)
# ---------------------------------------------------------------------------
LESSONS = [
    {
        "id": "lesson-001",
        "title": "Understanding Your Camera",
        "description": "Get comfortable with your camera body, buttons, and menus.",
        "content": "In this lesson you will learn the physical layout of a DSLR or mirrorless camera. We cover the shutter button, mode dial, exposure compensation, ISO button, and the importance of reading your histogram.",
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
        "content": "Aperture controls depth of field and light intake. Shutter speed freezes or blurs motion. ISO amplifies the sensor signal but adds noise. This lesson uses interactive examples so you can see the effect of each variable in isolation.",
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
        "content": "Good composition turns a snapshot into a photograph. We start with the rule of thirds grid overlay, then explore leading lines, natural frames, foreground interest for depth, and the power of negative space.",
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
        "content": "Hard light creates harsh shadows. Soft light wraps around your subject. Direction changes mood dramatically. Colour temperature shifts from cool blue of shade to warm gold of sunrise. We show how to read and use natural light at any time of day.",
        "category": "fundamentals",
        "difficulty": "beginner",
        "duration_minutes": 20,
        "is_pro": False,
        "order": 4,
        "tags": ["light", "colour-temperature", "natural-light", "beginner"],
    },
    {
        "id": "lesson-005",
        "title": "Shooting in Manual Mode",
        "description": "Take full creative control with Manual (M) mode.",
        "content": "Manual mode lets you set aperture, shutter speed, and ISO independently. We walk through a typical portrait scenario and a landscape scenario. You will learn to use the in-camera exposure meter and when to override it.",
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
        "content": "This lesson covers the classic portrait lighting patterns: Rembrandt, loop, split, and butterfly. We then discuss posing and how to give direction without making subjects feel awkward.",
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
        "content": "Street photography is about authentic human moments. We discuss the legal and ethical considerations of photographing strangers, then cover practical technique: zone focusing, shooting from the hip, using reflections and shadows.",
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
        "content": "Golden hour offers warm directional light perfect for landscapes. We cover location scouting, tripod technique, hyperfocal distance, ND filters for long exposures, and a basic Lightroom processing workflow.",
        "category": "landscape",
        "difficulty": "intermediate",
        "duration_minutes": 40,
        "is_pro": False,
        "order": 8,
        "tags": ["landscape", "golden-hour", "tripod", "ND-filter", "intermediate"],
    },
    {
        "id": "lesson-009",
        "title": "Advanced Flash Techniques: Off-Camera Lighting",
        "description": "Use speedlights and strobes to sculpt light like a pro.",
        "content": "Moving your flash off-camera transforms your images. We cover radio triggers, bare flash versus softboxes, the inverse square law, mixing flash with ambient light using high-speed sync, and a one-light portrait setup.",
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
        "content": "We go beyond basic sliders into HSL adjustments, the Tone Curve, the Colour Mixer, and Camera Calibration panel. You will learn how to create presets for batch processing and develop a signature colour palette.",
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
        "content": "Wildlife photography demands fast autofocus, patience, and fieldcraft. We cover continuous AF modes, subject recognition AF, burst shooting strategy, understanding animal behaviour to anticipate the peak moment.",
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
        "content": "Turning passion into profit requires business skills. We cover pricing your work, building a portfolio website, social media strategy, writing contracts that protect you, and how to handle difficult clients professionally.",
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
def main():
    # Delete existing collection if it exists
    try:
        client.collections["photography-lessons"].delete()
        print("🗑️  Deleted existing collection.")
    except Exception:
        pass

    # Create collection
    client.collections.create(SCHEMA)
    print("✅ Collection created.")

    # Upload lessons
    result = client.collections["photography-lessons"].documents.import_(
        LESSONS, {"action": "upsert"}
    )
    failed = [r for r in result if not r.get("success")]
    print(f"✅ Uploaded {len(LESSONS) - len(failed)} lesson(s). Failed: {len(failed)}.")
    if failed:
        for r in failed:
            print(f"  ❌ {r}")
        sys.exit(1)

    print("\n🎉 Done. Typesense index is ready.")


if __name__ == "__main__":
    main()