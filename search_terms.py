import os

search_terms = ["CA_UPDATE", "Shadow Trust", "100% Mesh", "rotation.phase"]

for root, dirs, files in os.walk("src"):
    for file in files:
        if file.endswith(".java"):
            path = os.path.join(root, file)
            with open(path, "r", encoding="utf-8", errors="ignore") as f:
                content = f.read()
                for term in search_terms:
                    if term in content:
                        print(f"Found '{term}' in {path}")
