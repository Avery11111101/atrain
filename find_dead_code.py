import os
import re

src_dir = "src/main/java"
all_files = []
for root, dirs, files in os.walk(src_dir):
    for file in files:
        if file.endswith(".java"):
            all_files.append(os.path.join(root, file))

content_map = {}
all_content = ""
for f in all_files:
    with open(f, 'r') as file:
        content = file.read()
        content_map[f] = content
        all_content += content + "\n"

# Regex for methods: captures optional annotations, then method signature
method_pattern = re.compile(r'(@EventHandler\s+)?(?:public|private|protected)\s+(?:static\s+)?(?:[\w<>,\[\]\s]+)\s+(\w+)\s*\(')

methods = set()
for f, content in content_map.items():
    for match in method_pattern.finditer(content):
        # ignore if it has @EventHandler
        if match.group(1):
            continue
        method_name = match.group(2)
        if not method_name.startswith("on"):
            methods.add(method_name)

ignore_list = {
    "main", "run", "equals", "hashCode", "toString", "clone"
}

suspicious = []
for method in methods:
    if method in ignore_list:
        continue
    occurrences = len(re.findall(r'\b' + method + r'\b', all_content))
    if occurrences == 1:
        suspicious.append(method)

for s in sorted(suspicious):
    print("Potential unused method:", s)
