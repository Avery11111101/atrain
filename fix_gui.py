import re

with open("src/main/java/com/avery/atrain/gui/GuiManager.java", "r") as f:
    content = f.read()

# Replace Bukkit.createInventory(holder, <size>, <string>) with Bukkit.createInventory(holder, <size>, TextUtil.component(<string>))
# The string part could be msg(...) or a variable like `title`
content = re.sub(r'(Bukkit\.createInventory\s*\(\s*holder\s*,\s*\d+\s*,\s*)(.*?)\s*\)', r'\1TextUtil.component(\2))', content)

with open("src/main/java/com/avery/atrain/gui/GuiManager.java", "w") as f:
    f.write(content)

