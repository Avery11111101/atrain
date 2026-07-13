import os
import re

src_dir = "src/main/java"
all_files = []
for root, dirs, files in os.walk(src_dir):
    for file in files:
        if file.endswith(".java"):
            all_files.append(os.path.join(root, file))

all_content = ""
for f in all_files:
    with open(f, 'r') as file:
        all_content += file.read() + "\n"

classes = []
for f in all_files:
    basename = os.path.basename(f)
    classname = basename.replace(".java", "")
    classes.append(classname)

suspicious = []
for c in classes:
    # exclude main plugin class AtrainPlugin
    if c == "AtrainPlugin":
        continue
    occurrences = len(re.findall(r'\b' + c + r'\b', all_content))
    if occurrences == 1:
        suspicious.append(c)

for s in sorted(suspicious):
    print("Potential unused class:", s)
