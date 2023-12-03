from pathlib import Path
import json

ROOT_ID = "0000000000000000"

pathlist = Path("/Users/jamie/opentelemetry-plugin/result").glob('**/trace*')
pipeline_map = {}
for path in pathlist:
     # because path is object not string
     # print(str(path))
     f = open(path)
     try:
          data = json.load(f)
          # print(data["parentSpanId"])
          if data["parentSpanId"] in pipeline_map:
               pipeline_map[data["parentSpanId"]].append(data)
          else:
                pipeline_map[data["parentSpanId"]] = [data]
     except Exception as e:
          print(e)
          continue
     # print(path_in_str)

# print(json.dumps(pipeline_map, indent=2))

result_object = pipeline_map[ROOT_ID]
queue = []
queue.extend(pipeline_map[ROOT_ID])

while len(queue) > 0:
     cursor_object = queue.pop(0)
     if cursor_object["spanId"] in pipeline_map:
          child_objects = pipeline_map[cursor_object["spanId"]]
          cursor_object["children"] = child_objects
          queue.extend(child_objects)


# print(json.dumps(result_object[0], indent=2))

for result in result_object:
     with open('result-' + result["traceId"] +'.json', 'w') as f:
          json.dump(result, f, indent=2)