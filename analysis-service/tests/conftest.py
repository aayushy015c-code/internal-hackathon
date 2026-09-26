import os
import sys

# tests never need the heavy audio libraries or the core API
os.environ["STUB_MODE"] = "true"
os.environ["CORE_API_URL"] = "http://127.0.0.1:9"  # nothing listens here
sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))
