默认角色素材为本项目生成的简单示例透明 PNG：mascot_head.png / mascot_body.png。可以用你自己的透明 PNG 替换，建议保持头部约 256x220、身体约 256x300，并调整 Kotlin 中显示尺寸与旋转中心。

Fox branch (floatingpet):
- mascot_fullbody.png: transparent PNG, full figure in one image, including long hair, head, neck and body. This is the fixed background layer so the head/body connection never separates.
- mascot_head.png: transparent PNG head overlay aligned to the head position in mascot_fullbody.png. Keep its face/hair silhouette aligned with the background artwork; it is only for subtle foreground motion.
- If mascot_fullbody.png is not present, the app falls back to mascot_body.png so the build can still run, but the long-haired unified artwork will not appear until the custom PNG is added.
