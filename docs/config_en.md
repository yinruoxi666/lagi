# Configuration Reference Guide

System Homepage Display Name: This setting specifies the name displayed on the system homepage, which is "LinkMind".

```yaml
system_title: LinkMind
```

Model Configuration

```yaml
# This section defines the model configuration used by the middleware.
models:
  # Configuration for a single-driver model.
  - name: chatgpt  # Name of the backend service.
    type: OpenAI  # Associated company, e.g., OpenAI here.
    enable: false # This flag determines whether the backend service is enabled. "true" means enabled.
    model: gpt-3.5-turbo,gpt-4-turbo # List of models supported by the driver.
    driver: ai.llm.adapter.impl.GPTAdapter # Model driver.
    api_key: your-api-key # API key.
  # Configuration for multi-driver models.
  - name: landing
    type: Landing
    enable: false
    drivers: # Multi-driver configuration.
      - model: turing,qa,tree,proxy # Driver Model List
        driver: ai.llm.adapter.impl.LandingAdapter # Driver address.
      - model: image # List of features supported by the driver.
        driver: ai.image.adapter.impl.LandingImageAdapter # Driver address.
        oss: landing # Name of the object storage service used.
      - model: landing-tts,landing-asr
        driver: ai.audio.adapter.impl.LandingAudioAdapter 
      - model: video
        driver: ai.video.adapter.impl.LandingVideoAdapter 
        api_key: your-api-key # API key specified for the driver.
    api_key: your-api-key # Shared API key for the drivers.
```

Storage Configuration

```yaml
# This section defines the storage device configuration used by the middleware.
stores:
  # Configuration for the vector database.
  vectors: # List of vector database configurations.
    - name: chroma # Name of the vector database.
      driver: ai.vector.impl.ChromaVectorStore # Vector database driver.
      default_category: default # Category for vector database storage.
      similarity_top_k: 10 # Parameter used for vector database queries.
      similarity_cutoff: 0.5 # Will cut off those results whose similarity to the query vector is less than 0.5.
      parent_depth: 1
      child_depth: 1
      url: http://localhost:8000 # Storage configuration of the vector database.
  # Configuration for object storage services.
  oss:
    - name: landing # Name of the object storage service.
      driver: ai.oss.impl.LandingOSS  # Object storage service driver class.
      bucket_name: lagi # Bucket name for object storage.
      enable: true # Determines if the object storage service is enabled.

    - name: alibaba
      driver: ai.oss.impl.AlibabaOSS
      access_key_id: your-access-key-id # Access key ID for third-party object storage services.
      access_key_secret: your-access-key-secret # Access key secret for third-party object storage services.
      bucket_name: ai-service-oss
      enable: true

  # Configuration for Elasticsearch.
  text:
    - name: elasticsearch # Name of the full-text search.
      driver: ai.bigdata.impl.ElasticSearchAdapter
      host: localhost # Address of Elasticsearch for full-text search.
      port: 9200 # Port of Elasticsearch for full-text search.
      enable: false # Determines if it is enabled.
  database: # Relational database configuration.
    name: mysql # Database name.
    jdbcUrl: you-jdbc-url # Connection address.
    driverClassName: com.mysql.cj.jdbc.Driver # Driver class.
    username: your-username # Database username.
    password: your-password # Database password.
  # Configuration for Retrieval-Augmented Generation (RAG) service.
  rag:
      vector: chroma # Name of the vector database used by the service.
      fulltext: elasticsearch # Full-text search (optional; if configured, this service is enabled. To disable, simply comment out this line).
      graph: landing # Graph search (optional; if configured, this service is enabled. To disable, simply comment out this line).
      enable: true # Determines if it is enabled.
      priority: 10 # Priority; if this priority exceeds the model's, it will return the default prompt if no context is matched.
      default: "Please give prompt more precisely" # Default prompt returned when no context is matched.
      track: true # Enables document tracking.
      
  # This section contains the configuration for Medusa's accelerated inference service. 
  # You can use the pre-trained `medusa.model` to prepopulate the cache. 
  # Set `flush` to true for the initial run to initialize it; afterward, you can change it back to false for routine start/stop operations.
  # Full download link for the `medusa.model` file: https://downloads.landingbj.com/lagi/medusa.model
  medusa:
      enable: true # Whether to enable
      algorithm: hash,llm,tree # Algorithms to use
      reason_model: deepseek-r1 # Inference model
      aheads: 1 # Number of pre-inference requests
      producer_thread_num: 1 # Number of producer threads
      consumer_thread_num: 2 # Number of consumer threads
      cache_persistent_path: medusa_cache # Cache persistence path
      cache_persistent_batch_size: 2 # Cache persistence batch size
      cache_hit_window: 16    # size of the sliding window for cache hits
      cache_hit_ratio: 0.3    # minimum cache hit ratio
      temperature_tolerance: 0.1  # tolerance for the temperature parameter on cache hits
      flush: true # Whether to reload the cache on every startup
```

Middleware Functionality Configuration

```yaml
# Functionality configuration for large models.
functions:
  # Embedding service configuration.
  embedding:
    - backend: qwen
      type: Qwen
      api_key: your-api-key
  
  # Configuration list for chat and text generation functions.
  chat:
    - backend: chatgpt # Name of the backend model configuration.
      model: gpt-4-turbo # Model name.
      enable: true # Determines if it is enabled.
      stream: true # Determines if streaming is used.
      priority: 200 # Priority.

    - backend: chatglm
      model: glm-3-turbo
      enable: false
      stream: false
      priority: 10
  
  # Configuration list for translation functions.
  translate:
    - backend: ernie # Name of the backend model configuration.
      model: translate # Model name.
      enable: false # Determines if it is enabled.
      priority: 10 # Priority.
  
  # Configuration list for speech-to-text functions.
  speech2text:
    - backend: qwen  # Name of the backend model configuration.
      model: asr
      enable: true
      priority: 10
  
  # Configuration list for text-to-speech functions.
  text2speech:
    - backend: landing # Name of the backend model configuration.
      model: tts
      enable: true
      priority: 10
  
  # Configuration list for voice cloning functions.
  speech2clone:
    - backend: doubao # Name of the backend model configuration.
      model: openspeech
      enable: true
      priority: 10
      others: your-speak-id

  # Configuration list for text-to-image functions.
  text2image:
    - backend: spark # Name of the backend model configuration.
      model: tti
      enable: true
      priority: 10
    - backend: ernie
      model: Stable-Diffusion-XL
      enable: true
      priority: 5
  # Configuration list for image-to-text functions.
  image2text:
    - backend: ernie # Name of the backend model configuration.
      model: Fuyu-8B
      enable: true
      priority: 10
  # Configuration list for image enhancement functions.
  image2enhance:
    - backend: ernie # Name of the backend model configuration.
      model: enhance
      enable: true
      priority: 10
  # Configuration list for text-to-video functions.
  text2video:
    - backend: landing # Name of the backend model configuration.
      model: video
      enable: true
      priority: 10
  # Configuration list for image-to-video functions.
  image2video:
    - backend: qwen # Name of the backend model configuration.
      model: vision
      enable: true
      priority: 10
  # Configuration list for image OCR functions.
  image2ocr:
    - backend: qwen
      model: ocr
      enable: true
      priority: 10
  # Configuration list for video tracking functions.
  video2track:
    - backend: landing # Name of the backend model configuration.
      model: video
      enable: true
      priority: 10
  # Configuration list for video enhancement functions.
  video2enhance:
    - backend: qwen # Name of the backend model configuration.
      model: vision
      enable: true
      priority: 10
  # Configuration list for document OCR functions.
  doc2ocr:
    - backend: qwen
      model: ocr
      enable: true
      priority: 10
  # Configuration list for document instruction functions.
  doc2instruct:
    - backend: landing
      model: cascade
      enable: true
      priority: 10
  # Configuration list for SQL instruction functions.
  text2sql:
    - backend: landing
      model: qwen-turbo # Model name.
      enable: true # Determines if it is enabled.
      priority: 10
```

Routing Policy Configuration

```yaml
functions:
  policy:
    # Handle configuration currently supports parallel, failover, and polling:
    #  1. If no model is explicitly specified in the request or the specified model is invalid, the three strategies apply.
    #  2. "parallel" executes configured models concurrently, returning the fastest and highest-priority result.
    #  3. "failover" executes models sequentially by priority, stopping when a successful result is obtained.
    #  4. "polling" distributes requests evenly among models using parameters such as IP and browser fingerprints.
    #  5. If all models fail, the HTTP status code is set to 600–608, with the body containing detailed error information.
    #     Error codes:
    #       600 Invalid request parameters.
    #       601 Authorization error.
    #       602 Permission denied.
    #       603 Resource not found.
    #       604 Rate limit exceeded.
    #       605 Model internal error.
    #       606 Other errors.
    #       607 Timeout.
    #       608 No available model.
    handle: failover #parallel #failover
    grace_time: 20 # Retry interval after failure.
    maxgen: 3 # Maximum retries after failure (default is Integer.MAX_VALUE).
```

Agent Configuration

```yaml
# This section represents agent configurations supported by the models.
agents:
  - name: qq # Agent name.
    api_key: your-api-key # API key used by the agent.
    driver: ai.agent.social.QQAgent # Agent driver.

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
    
# This section represents configurations for agent workers.
workers:
  - name: qq-robot # Worker name.
    agent: qq # Name of the agent it works with.
    worker: ai.worker.social.RobotWorker # Worker driver.

  - name: wechat-robot
    agent: wechat
    worker: ai.worker.social.RobotWorker

  - name: ding-robot
    agent: ding
    worker: ai.worker.social.RobotWorker

# Routing configuration.
routers:
  - name: best
    # Rule: (weather_agent&food_calorie_agent)
    # A|B -> Polling, A or B indicates random polling between A and B.
    # A,B -> Failover, starting with A; if A fails, then B.
    # A&B -> Parallel execution, calling A and B simultaneously and selecting the most appropriate single result.
    # This rule can combine into ((A&B&C),(E|F)), meaning first simultaneously call A, B, and C, and if they fail, randomly call E or F.
    rule: (weather_agent&food_calorie_agent)

    # % represents a wildcard.
    # If specified, the call will use the given agent.
    # If only "%" is given, the % will be determined by the parameters passed during the call.
  - name: pass
    rule: '%'

```

MCP configuration

```yaml
mcps:
  servers:
    - name: baidu_search_mcp  # MCP service name
      url: http://appbuilder.baidu.com/v2/ai_search/mcp/sse?api_key=Bearer+your_api_key  # MCP service URL
```

Skills Configuration

```yaml
# Skills configuration
skills:
  # Skill root paths. Supports classpath or local directories.
  roots: ["classpath:skills"]
  # Skill workspace directory.
  workspace: "skills"
  # Rule: server (server-first) / cli (client-first) / block (disabled)
  rule: cli
  # Tell the server which skills are available.
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
