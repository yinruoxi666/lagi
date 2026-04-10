# 配置参考指南

系统首页展示名称，这个设置指定了将在系统首页显示的名称，这里是“ LinkMind”。

```yaml
system_title: LinkMind
```

模型配置

```yaml
# 这部分定义了中间件使用的模型配置。
models:
  # 单驱动模型情况下模型的配置。
  - name: chatgpt  # 后端服务的名称。
    type: OpenAI  # 所属公司，例如这里是OpenAI
    enable: false # 这个标志决定了后端服务是否启用。“true”表示已启用。
    model: gpt-3.5-turbo,gpt-4-turbo # 驱动支持的模型列表
    driver: ai.llm.adapter.impl.GPTAdapter # 模型驱动
    api_key: your-api-key # API密钥
  # 模型支持多驱动的配置
  - name: landing
    type: Landing
    enable: false
    drivers: # 多驱动配置.
      - model: turing,qa,tree,proxy # 驱动模型列表
        driver: ai.llm.adapter.impl.LandingAdapter # 驱动地址
      - model: image # 驱动支持功能列表
        driver: ai.image.adapter.impl.LandingImageAdapter # 驱动地址
        oss: landing # 用到的存储对象服务的名称
      - model: landing-tts,landing-asr
        driver: ai.audio.adapter.impl.LandingAudioAdapter 
      - model: video
        driver: ai.video.adapter.impl.LandingVideoAdapter 
        api_key: your-api-key # 驱动指定api_key
    api_key:  your-api-key # 驱动公用的api_key

```

存储功能配置

```yaml
# 这部分定义了中间件用到的存储设备配置。
stores:
  # 这部分是向量数据库的配置
  vectors: # 向量数据库配置列表
    - name: chroma # 向量数据库名称
      driver: ai.vector.impl.ChromaVectorStore # 向量数据库驱动
      default_category: default # 向量数据库存储的分类
      similarity_top_k: 10 # 向量数据库查询时使用的参数
      similarity_cutoff: 0.5 # 会切掉那些与查询向量相似度 低于 0.5 的结果。
      parent_depth: 1
      child_depth: 1
      url: http://localhost:8000 # 向量数据库的存储配置
  # 这部分是对象存储服务的配置
  oss:
    - name: landing # 存储对象服务的名称
      driver: ai.oss.impl.LandingOSS  # 存储对象服务的驱动类
      bucket_name: lagi # 存储对象的 bucket_name
      enable: true # 是否开启该存储对象服务

    - name: alibaba
      driver: ai.oss.impl.AlibabaOSS
      access_key_id: your-access-key-id # 第三方存储对象的服务用到的 access key id
      access_key_secret: your-access-key-secret # 第三方存储对象的服务用到的 access key secret 
      bucket_name: ai-service-oss
      enable: true

  # 这部分是elasticsearch的配置
  text:
    - name: elasticsearch # 全文检索名称
      driver: ai.bigdata.impl.ElasticSearchAdapter
      host: localhost # 全文检索的elasticsearch地址
      port: 9200 # 全文检索的elasticsearch的端口号
      enable: false # 是否开启
  database: # 关系型数据库配置
    name: mysql # 数据库名称
    jdbcUrl: you-jdbc-url # 连接地址
    driverClassName: com.mysql.cj.jdbc.Driver # 驱动类
    username: your-username # 数据库用户名
    password: your-password # 数据库密码
  # 这部分是检索增强生成服务的配置
  rag:
      vector: chroma # 服务用到的向量数据库的名称
      fulltext: elasticsearch # 全文检索（可选填，如填写该配置，则开启该配置，不开启，直接注释即可）
      graph: landing # 图检索（可选填，如填写该配置，则开启该配置，不开启，直接注释即可）
      enable: true # 是否开启
      priority: 10 # 优先级，当该优先级大于模型时,则匹配不到上下文就只返回default中提示语
      default: "Please give prompt more precisely" # 如未匹配到上下文，则返回该提示语
      track: true # 开启文档跟踪
  # 这部分是美杜莎的加速推理服务的配置，可以通过预训练的medusa.model来预准备缓存，第一次flush置成true来初始化，后续可以改回false用做日常启停。
  # 完整的medusa.model文件下载地址：https://downloads.landingbj.com/lagi/medusa.model
  medusa:
    enable: true # 是否开启
    algorithm: hash,llm,tree # 使用的算法
    reason_model: deepseek-r1 # 推理模型
    aheads: 1 # 预推理的请求数
    producer_thread_num: 1 # 生产者线程数
    consumer_thread_num: 2 # 消费者线程数
    cache_persistent_path: medusa_cache # 缓存持久化路径
    cache_persistent_batch_size: 2 # 缓存持久化批次大小
    cache_hit_window: 16 # cache命中的滑动窗口大小
    cache_hit_ratio: 0.3 # cache命中的最低比例
    temperature_tolerance: 0.1  # cache命中时温度参数的容忍度
    flush: true # 缓存是否每次启动时都重新加载
```

中间件功能配置

```yaml
# 大模型使用的功能配置
functions:
  # embedding 服务配置
  embedding:
    - backend: qwen
      type: Qwen
      api_key: your-api-key
  
  # 聊天对话、文本生成功能的配置列表
  chat:
    - backend: chatgpt # 后端使用的模型配置的名称
      model: gpt-4-turbo # 模型名
      enable: true # 是否开启
      stream: true # 是否使用流
      priority: 200 # 优先级

    - backend: chatglm
      model: glm-3-turbo
      enable: false
      stream: false
      priority: 10
  
  # 翻译功能的配置列表
  translate:
    - backend: ernie # 后端使用的模型配置的名称
      model: translate # 模型名
      enable: false # 是否开启
      priority: 10 # 优先级
  
  # 语音转文字功能配置列表
  speech2text:
    - backend: qwen  # 后端使用的模型配置的名称
      model: asr
      enable: true
      priority: 10
  
  # 文字转语音功能配置列表
  text2speech:
    - backend: landing # 后端使用的模型配置的名称
      model: tts
      enable: true
      priority: 10
  
  # 声音克隆功能配置列表
  speech2clone:
    - backend: doubao # 后端使用的模型配置的名称
      model: openspeech
      enable: true
      priority: 10
      others: your-speak-id

  # 文字生成图片功能配置列表
  text2image:
    - backend: spark # 后端使用的模型配置的名称
      model: tti
      enable: true
      priority: 10
    - backend: ernie
      model: Stable-Diffusion-XL
      enable: true
      priority: 5
  # 图片生成文字功能配置列表
  image2text:
    - backend: ernie # 后端使用的模型配置的名称
      model: Fuyu-8B
      enable: true
      priority: 10
  # 图片增强功能配置列表
  image2enhance:
    - backend: ernie # 后端使用的模型配置的名称
      model: enhance
      enable: true
      priority: 10
  # 文本生成视频功能配置列表
  text2video:
    - backend: landing # 后端使用的模型配置的名称
      model: video
      enable: true
      priority: 10
  # 图片生成视频功能配置列表
  image2video:
    - backend: qwen # 后端使用的模型配置的名称
      model: vision
      enable: true
      priority: 10
  # 图片OCR配置列表 OCR（Optical Character Recognition，光学字符识别）是将图片中的文字内容识别并提取为可编辑文本的技术
  image2ocr:
    - backend: qwen
      model: ocr
      enable: true
      priority: 10
  # 视频追踪功能配置列表
  video2track:
    - backend: landing # 后端使用的模型配置的名称
      model: video
      enable: true
      priority: 10
  # 视屏增强功能配置列表
  video2enhance:
    - backend: qwen # 后端使用的模型配置的名称
      model: vision
      enable: true
      priority: 10
  # 文档OCR配置列表 OCR（Optical Character Recognition，光学字符识别）是将图片中的文字内容识别并提取为可编辑文本的技术
  doc2ocr:
    - backend: qwen
      model: ocr
      enable: true
      priority: 10
  # 文件指令配置列表
  doc2instruct:
    - backend: landing
      model: cascade
      enable: true
      priority: 10
  # sql指令配置表
  text2sql:
    - backend: landing
      model: qwen-turbo # 模型名称
      enable: true # 是否启用
      priority: 10
```

路由政策配置

```yaml
functions:
  policy:
    #  handle配置 目前有parallel、failover、failover 3种值， parallel表示并行调用，failover表示故障转移, polling表示负载轮询调用, 场景解释：
    #  1. 当请求中未强制指定模型, 或指定的模型无效时 ，parallel、failover、failover 3种策略生效
    #  2. 当指定handle为 parallel 配置的模型并行执行， 返回响应最快且优先级最高的模型调用结果
    #  3. 当指定handle为 failover 配置的模型串行执行， 模型按优先级串行执行， 串行执行过程中任意模型返回成功， 后面的模型不再执行。
    #  4. 当指定handle为 failover 配置的模型轮询执行， 请求会根据请求的ip、浏览器指纹 等额外信息， 均衡分配请求给对应的模型执行。
    #  5. 当所有的模型都返回失败时, 会设置 http 请求的状态码为 600-608。 body里为具体的错误信息。 (错误码错误信息实际为最后一个模型调用失败的信息)
    #  错误码： 
    #     600 请求参数不合法
    #     601 授权错误
    #     602 权限被拒绝
    #     603 资源不存在
    #     604 访问频率限制
    #     605 模型内部错误
    #     606 其他错误
    #     607 超时
    #     608 没有可用的模型
    handle: failover #parallel #failover
    grace_time: 20 # 故障后重试间隔时间
    maxgen: 3 # 故障后最大重试次数 默认为 Integer.MAX_VALUE
```

智能体配置

```yaml
# 这部分表示模型支持的智能体配置
agents:
  - name: qq # 智能体的名称
    api_key: your-api-key # 智能体用到的 api key
    driver: ai.agent.social.QQAgent # 智能体驱动

  - name: wechat
    api_key: your-api-key
    driver: ai.agent.social.WechatAgent

  - name: ding
    api_key: your-api-key
    driver: ai.agent.social.DingAgent
    
  - name: weather_agent
    driver: ai.agent.customer.WeatherAgent
    token: your-token
    app_id: weather_agent

  - name: oil_price_agent
    driver: ai.agent.customer.OilPriceAgent
    token: your-token
    app_id: oil_price_agent

  - name: bmi_agent
    driver: ai.agent.customer.BmiAgent
    token: your-token
    app_id: bmi_agent

  - name: food_calorie_agent
    driver: ai.agent.customer.FoodCalorieAgent
    token: your-token
    app_id: food_calorie_agent

  - name: dishonest_person_search_agent
    driver: ai.agent.customer.DishonestPersonSearchAgent
    token: your-token
    app_id: dishonest_person_search_agent

  - name: high_speed_ticket_agent
    driver: ai.agent.customer.HighSpeedTicketAgent
    app_id: high_speed_ticket_agent

  - name: history_in_today_agent
    driver: ai.agent.customer.HistoryInToDayAgent
    app_id: history_in_today_agent

  - name: youdao_agent
    driver: ai.agent.customer.YouDaoAgent
    app_id: your-app-id
    token: your-token

  - name: image_gen_agent
    driver: ai.agent.customer.ImageGenAgent
    app_id: your-app-id
    endpoint: http://127.0.0.1:8080
    token: image_gen_agent
    
# 这部分表示智能体实际作业的配置
workers:
  - name: qq-robot # 作业名称
    agent: qq # 工作的智能体名称
    worker: ai.worker.social.RobotWorker # 作业驱动

  - name: wechat-robot
    agent: wechat
    worker: ai.worker.social.RobotWorker

  - name: ding-robot
    agent: ding
    worker: ai.worker.social.RobotWorker

# 路由配置
routers:
  - name: best
    # rule: (weather_agent&food_calorie_agent)  # A|B ->轮询，A或B，表示在A和B之间随机轮询；
    # A,B ->故障转移，首先A，如果A失败，然后B；
    # A&B ->并行，同时调用A和B，选择合适的结果只有一个
    # 该规则可以组合为((A&B&C),(E|F))，这意味着首先同时调用ABC，如果失败，则随机调用E或F
    rule: (weather_agent&food_calorie_agent)  # A|B ->轮询，A或B，表示在A和B内随机轮询；

    # %是表示的通配符。
    # 如果指定，则调用该代理
    # 如果只给出%，则%将由调用时的参数决定。
  - name: pass
    rule: '%'
```

MCP配置

```yaml
mcps:
  servers:
    - name: baidu_search_mcp # mcp服务名称
      url: http://appbuilder.baidu.com/v2/ai_search/mcp/sse?api_key=Bearer+your_api_key # mcp服务地址
```

技能配置（Skills）

```yaml
# 技能配置
skills:
  # 技能根路径，支持 classpath 或本地目录
  roots: ["classpath:skills"]
  # 技能工作目录
  workspace: "skills"
  # 规则：server（服务端优先）/ cli（客户端优先）/ block（不启用）
  rule: cli
  # 告诉服务端当前可用的技能列表
  items:
    - name: extract_content_with_image
      description: 将本地 PDF、TXT、Word、PPT 文件分割为文本和图片chunk。
      rule: server
    - name: pdf
      description: Use this skill whenever the user wants to do anything with PDF files. This includes reading or extracting text/tables from PDFs, combining or merging multiple PDFs into one, splitting PDFs apart, rotating pages, adding watermarks, creating new PDFs, filling PDF forms, encrypting/decrypting PDFs, extracting images, and OCR on scanned PDFs to make them searchable. If the user mentions a .pdf file or asks to produce one, use this skill.
    - name: docx
      description: "Use this skill whenever the user wants to create, read, edit, or manipulate Word documents (.docx files). Triggers include: any mention of 'Word doc', 'word document', '.docx', or requests to produce professional documents with formatting like tables of contents, headings, page numbers, or letterheads. Also use when extracting or reorganizing content from .docx files, inserting or replacing images in documents, performing find-and-replace in Word files, working with tracked changes or comments, or converting content into a polished Word document. If the user asks for a 'report', 'memo', 'letter', 'template', or similar deliverable as a Word or .docx file, use this skill. Do NOT use for PDFs, spreadsheets, Google Docs, or general coding tasks unrelated to document generation."
    - name: pptx
      description: "Use this skill any time a .pptx file is involved in any way — as input, output, or both. This includes: creating slide decks, pitch decks, or presentations; reading, parsing, or extracting text from any .pptx file (even if the extracted content will be used elsewhere, like in an email or summary); editing, modifying, or updating existing presentations; combining or splitting slide files; working with templates, layouts, speaker notes, or comments. Trigger whenever the user mentions \"deck,\" \"slides,\" \"presentation,\" or references a .pptx filename, regardless of what they plan to do with the content afterward. If a .pptx file needs to be opened, created, or touched, use this skill."
    - name: xlsx
      description: "Use this skill any time a spreadsheet file is the primary input or output. This means any task where the user wants to: open, read, edit, or fix an existing .xlsx, .xlsm, .csv, or .tsv file (e.g., adding columns, computing formulas, formatting, charting, cleaning messy data); create a new spreadsheet from scratch or from other data sources; or convert between tabular file formats. Trigger especially when the user references a spreadsheet file by name or path — even casually (like \"the xlsx in my downloads\") — and wants something done to it or produced from it. Also trigger for cleaning or restructuring messy tabular data files (malformed rows, misplaced headers, junk data) into proper spreadsheets. The deliverable must be a spreadsheet file. Do NOT trigger when the primary deliverable is a Word document, HTML report, standalone Python script, database pipeline, or Google Sheets API integration, even if tabular data is involved."
    - name: screenshot-1.0.1
      description: "Capture, inspect, and compare screenshots of screens, windows, regions, web pages, simulators, and CI runs with the right tool, wait strategy, viewport, and output format. Use when (1) you need screenshots for debugging, QA, docs, bug reports, or visual review; (2) desktop, browser, simulator, or headless capture is involved; (3) stable screenshots require fixed viewport, settling, masking, or animation control."
    - name: cn-web-search-2.2.0
      description: 中文网页搜索 - 聚合 17 个免费搜索引擎，无需 API Key，纯网页抓取，支持公众号/财经/技术/学术/知识搜索。
    - name: baidu-search-1.1.3
      description: Search the web using Baidu AI Search Engine (BDSE). Use for live information, documentation, or research topics.
    - name: google-maps-3.2.0
      description: "Google Maps integration for OpenClaw with Routes API. Use for: (1) Distance/travel time calculations with traffic prediction, (2) Turn-by-turn directions, (3) Distance matrix between multiple points, (4) Geocoding addresses to coordinates and reverse, (5) Places search and details, (6) Transit planning with arrival times. Supports future departure times, traffic models (pessimistic/optimistic), avoid options (tolls/highways), and multiple travel modes (driving/walking/bicycling/transit)."
    - name: feishu-1.0.5
      description: 飞书深度集成技能。不是简单的消息桥接，而是你的数字指挥中枢。专为中国企业高压协作环境设计，理解“分寸”与“效率”两套并行规则，把消息、审批、会议、文档、多维表格、日程与邮箱，压缩成有优先级、可执行的行动链。
    - name: imap-smtp-email-0.0.10
      description: Read and send email via IMAP/SMTP. Check for new/unread messages, fetch content, search mailboxes, mark as read/unread, and send emails with attachments. Supports multiple accounts. Works with any IMAP/SMTP server including Gmail, Outlook, 163.com, vip.163.com, 126.com, vip.126.com, 188.com, and vip.188.com.
    - name: docker-1.0.4
      description: "Docker containers, images, Compose stacks, networking, volumes, debugging, production hardening, and the commands that keep real environments stable. Use when (1) the task touches Docker, Dockerfiles, images, containers, or Compose; (2) build reliability, runtime behavior, logs, ports, volumes, or security matter; (3) the agent needs Docker guidance and should apply it by default."
    - name: typescript-skills-1.0.6
      description: Provide best-practice coding conventions and generate standards-compliant TypeScript code.
    - name: webapp-testing
      description: Toolkit for interacting with and testing local web applications using Playwright. Supports verifying frontend functionality, debugging UI behavior, capturing browser screenshots, and viewing browser logs.
    - name: ws-agent-browser-1.0.0
      description: 浏览器智能控制。自动化操作、截图、填表、数据抓取。
    - name: skill-finder-1.1.5
      description: "Find, compare, and install agent skills across ClawHub and Skills.sh when the user needs new capabilities, better workflows, stronger tools, or safer alternatives. Use when (1) they ask how to do something, how to improve or automate it, or what to install; (2) a skill could extend the agent, replace a weak manual approach, or close a capability gap; (3) you need the best-fit option, not just a direct answer."
```

