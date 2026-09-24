import importlib.util, json
spec = importlib.util.spec_from_file_location("gh", "tools/gh_release.py")
gh = importlib.util.module_from_spec(spec); spec.loader.exec_module(gh)
t = gh.token()
# 上传新资产（临时名避开重名）
data = open("build/libs/manhunt-0.2.0-beta.jar", "rb").read()
status, body = gh.request(
    "POST",
    "https://uploads.github.com/repos/TakaraMiyuki/Manhunt/releases/394282644/assets?name=manhunt-0.2.0-beta-new.jar",
    t, raw=data, content_type="application/java-archive")
body = json.loads(body) if isinstance(body, str) else body
print("upload:", status)
new_id = body["id"]
# 删除旧资产（582884079 = 上一次构建的 jar）
status, _ = gh.request(
    "DELETE", "https://api.github.com/repos/TakaraMiyuki/Manhunt/releases/assets/582884079", t)
print("delete old:", status)
# 重命名为正式名
status, _ = gh.request(
    "PATCH", "https://api.github.com/repos/TakaraMiyuki/Manhunt/releases/assets/" + str(new_id),
    t, payload={"name": "manhunt-0.2.0-beta.jar"})
print("rename:", status)
