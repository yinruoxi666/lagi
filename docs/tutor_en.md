# Tutorial Demonstration

LinkMind is a powerful enterprise-grade multimodal large model middleware that helps you seamlessly integrate large model technology into your business. This tutorial will guide you step-by-step through the download, installation, configuration, and operation of LinkMind, enabling you to master its usage quickly.

## Environment Setup

Before getting started, ensure you have the following environment ready:

* **Java 8 or higher**
* **Maven**
* **Docker (optional, for running vector databases)**

## 1. Download LinkMind

For developers, we provide an easy method to compile and run the LinkMind application. You can either use the Maven command-line tool to package it or run it through popular integrated development environments (IDEs) such as IntelliJ IDEA.

### Method 1: Use IDE+maven packaging

1. **Clone the project**:

**Open the GitHub repository**: Connect to LinkMind's GitHub repository in the IDE, and use the IDE's clone function to clone the LinkMind project to your local computer.

| | GitHub repository |
| ----- | ------------------------------------- |
| SSH | git@github.com:landingbj/lagi.git |
| HTTPS | https://github.com/landingbj/lagi.git |
```shell
git clone https://github.com/landingbj/lagi.git
```
2. **Enter the project**: Switch to the project directory:

```shell
cd lagi
```

3. **Compile the project**: Run the Maven command in the project root directory to compile:

```bash
mvn clean install
```

4. **Select IDE**: You can choose to use mainstream IDEs such as IntelliJ IDEA or Eclipse.

5. **Compile the project**: Use the compilation function of the IDE to compile the LinkMind project.

### Method 2: Use Web container (Tomcat)

Before installing tomcat, you must first install jdk1.8; check the relevant version

If idk1.8 is not installed, please refer to Note 1, install idk1.8 before proceeding to the next step

If Tomcat is not installed, please refer to Note 2, install Tomcat before proceeding to the next step

1. **Download War file**: LinkMind's Web application can be directly deployed to the Web container.
- File name: `lagi-web.war`
- Download link: [Click here to download](https://downloads.landingbj.com/lagi/lagi-web.war)

2. **Start the project**:
* Put the downloaded war package file into the unzipped Tomcat webapps path, such as:

```bash
apache-tomcat-8.5.99\webapps\
```

* If elastic and chroma are not installed locally, modify the corresponding configuration item in \apache-tomcat-8.5.99\webapps\ROOT\WEB-INF\classes\lagi.yml to enable: false

```yml
   term:
   # This is an adapter for Elasticsearch, providing search and analytics capabilities on large datasets.
   - name: elastic
     driver: ai.bigdata.impl.ElasticSearchAdapter
     host: localhost
     port: 9200
     enable: false

   rag: # RAG enhances large language models by integrating external knowledge retrieval.
      vector: chroma
      term: elastic
      graph: landing
      enable: false
      priority: 10
      default: "Please give prompt more precisely"
      track: true
```

* Open the executable file 'startup.bat', such as:

```bash
apache-tomcat-8.5.99\bin\startup.bat
```

You can visit http://localhost:8000/ through the browser to check whether the startup is successful

### Method 3: Docker

- Image name: `landingbj/lagi`

- Pull command:

```bash
docker pull landingbj/lagi 
```

- Start the container: 

```bash 
docker run -d --name lagi-web -p 8080:8080 landingbj/lagi
```

3. **Compile the Project**: Use the IDE's compile feature to build the LinkMind project.

## 2. Install a Vector Database

LinkMind supports various vector databases such as ChromaDB. If you want to use the retrieval-augmented generation (RAG) feature, you need to install a vector database.

**Using ChromaDB as an example**:

### Method 1: Python

*** Make sure the Python runtime environment is installed (download resources are at the end of the document Note 3) ***

- Install ChromaDB (command execution is executed in a black window)

```bash
pip install chromadb
```

- Create a database storage directory 'db_data'

```bash
mkdir db_data
```

- Start the database service ('db_data' is the name of the folder created in the previous step)

```bash
# The --path parameter can specify the data persistence path
# Port 8000 is enabled by default
chroma run --path db_data
```

**Note:**

If you encounter an error when importing the `chromadb` package due to an outdated sqlite3 version:

> RuntimeError: Your system has an unsupported version of sqlite3. Chroma requires sqlite3 >= 3.35.0.

Follow these steps:

1. Install `pysqlite3-binary`:

   ```bash
   pip install pysqlite3-binary
   ```

2. Modify the `__init__.py` file in the `chromadb` source code to override the original sqlite3 library:

   ```bash
   vim <path_to_chromadb>/__init__.py
   ```

3. Add the following lines at the beginning of the file:

   ```python
   __import__('pysqlite3')
   import sys
   sys.modules['sqlite3'] = sys.modules.pop('pysqlite3')
   ```

4. Restart the database service.

Once installed, you can verify the service is running by accessing: http://localhost:8000/docs.

![img_1.png](images/img_1.png)

## 3. Configuration file

Modify the `lagi.yml` configuration file, select the model you like, replace the `your-api-key` and other information of the model with your own key, and set the `enable` field of the enabled model to `true` as required.

***Take the configuration of kimi as an example:***

If it is started in Tomcat form, modify the path to: \apache-tomcat-8.5.99\webapps\ROOT\WEB-INF\classes\lagi.yml

- Fill in the model information and start the model, modify the enable setting to true.

```yaml
   - name: kimi
     type: Moonshot
     enable: true
     model: moonshot-v1-8k,moonshot-v1-32k,moonshot-v1-128k
     driver: ai.llm.adapter.impl.MoonshotAdapter
     api_key: your-api-key
```

- According to your needs, set the model output mode stream and priority priority. The larger the value, the higher the priority.

```yaml
   chat:
     - backend: doubao
       model: doubao-pro-4k
       enable: true
       stream: true
       priority: 160
   
     - backend: kimi
       model: moonshot-v1-8k
       enable: true
       stream: true
       priority: 150
```

- According to your needs, add route modification.

```yaml
   # Rule: (Xiaoxin Agent & Stock Agent & Exchange Rate Agent) # A|B -> Polling, A or B, means randomly polling between A and B;
   # A,B -> Failover, execute A first, if A fails, then execute B;
   # A&B -> Parallel, call A and B at the same time, and select the appropriate unique result;
   # This rule can be combined into ((A&B&C), (E|F)), which means calling ABC at the same time first, and if it fails, randomly call E or F.
   chat:
     route: best((landing&chatgpt), (kimi|ernie))
```

Select the configured vector database and fill in the corresponding configuration information.

***Example: Configuring a Local Chroma Database***

- Replace the URL with the Chroma service URL, e.g., [http://localhost:8000](http://localhost:8000/).

  ```yaml
  vectors:
    - name: chroma
      driver: ai.vector.impl.ChromaVectorStore
      default_category: default
      similarity_top_k: 10
      similarity_cutoff: 0.5
      parent_depth: 1
      child_depth: 1
      url: http://localhost:8000
  
  rag:
    vector: chroma
    graph: landing
    enable: true
    priority: 10
    default: "Please give prompt more precisely"
  ```

## 4. Add Dependencies

To call LinkMind APIs, add dependencies using Maven or import JAR files.

***Example: Adding Maven Dependencies***

- Download dependencies with the following command:

  ```shell
  mvn clean install
  ```

## 5. Start the Web Service

You can start the web service using either the Maven command-line tool or an IDE.

***Example: Using Maven***

1. Package the project:

   ```shell
   mvn package
   ```

2. Deploy the generated WAR file to a web server such as Tomcat. Start Tomcat, then access LinkMind via a browser, e.g., http://localhost:8080/.

![img.png](images/img.png)

## 6. Test LinkMind

Visit the LinkMind page using your browser. Test features such as text conversations, speech recognition, text-to-speech, and image generation.

![img_2.png](images/img_2.png)

## 7. Model Switching

LinkMind supports dynamic model switching. Update the `lagi.yml` configuration file to enable different models based on your needs. For non-streaming calls, LinkMind will automatically switch to another model based on priority if the current model fails.

```yaml
   - backend: chatglm
     model: glm-3-turbo
     enable: true
     stream: true
     priority: 10
   
   - backend: ernie
     model: ERNIE-Speed-128K
     enable: false
     stream: true
     priority: 10
```
- Add route modifications according to your needs.

```yaml
   # Rule: (Xiaoxin Agent & Stock Agent & Exchange Rate Agent) # A|B -> Polling, A or B, means randomly polling between A and B;
   # A,B -> Failover, execute A first, if A fails, then execute B;
   # A&B -> Parallel, call A and B at the same time, and select the appropriate unique result;
   # This rule can be combined into ((A&B&C), (E|F)), which means calling ABC at the same time first, and if it fails, randomly call E or F.
   chat:
     route: best((landing&chatgpt), (kimi|ernie))
```
Switch models online via the interface:

![img.png](images/img_3.png)

## 8. Extend Functionality

If LinkMind's supported models or vector databases do not meet your needs, refer to the [extension documentation](extend_zh.md) to adapt LinkMind for your preferred models or databases.

## 9. Custom Training with QA Pairs

Integrate internal data into LinkMind by uploading QA pairs, allowing you to customize and train a model tailored to your needs. Refer to the [API documentation](API_en.md) for details.

### **Custom Training Architecture**

![img.png](images/img_5.png)

### **Custom Training Workflow**

![img.png](images/img_6.png)

## 10. Generate Instruction Sets

Use the instruction set generation API, as detailed in the [API documentation](API_en.md).

**Criteria for Extracting QA Pairs:**

1. **Structured Extraction:** Extract questions and corresponding answers from the document and organize them into clear QA formats.
2. **Language Summarization:** Summarize key information from the original content to generate concise and accurate responses that precisely convey the intended meaning.
3. **Formatting:** Convert QA content into a specified JSON format for further training or analysis.
4. **Logical Segmentation:** Generate well-defined questions and answers based on the document's paragraph topics (e.g., background introduction, application scenarios, specific advantages).

## 11. Upload Custom Training Files

For different types of files, Lagi will adopt differentiated processing strategies to efficiently and automatically convert the file content into the ideal format for large model training, in order to improve the model's learning efficiency and performance, and help users train their own exclusive large models. The specific processing methods for each category are as follows:

### Supported File Formats

This feature supports the following file formats:
- Text formats: txt, doc, docx, pdf
- Spreadsheet formats: xls, xlsx, csv
- Image formats: jpeg, png, jpg, webp
- Presentation formats: ppt, pptx

### File processing methods

For different types of files, Lagi will adopt differentiated processing strategies to efficiently and automatically convert the file content into the optimal format for large models. This is to enhance the learning efficiency and performance of the models, helping users train their own exclusive large models. The specific categories are as follows:

1. **Q&A File Processing**:
    - For Q&A files, Lagi will use intelligent algorithms to analyze the content, precisely extract keywords, and effectively separate questions and answers to improve the model's learning efficiency and performance.

2. **Chapter-based File Processing**:
    - For files with a chapter structure, Lagi will prioritize removing non-content elements such as directories, then apply intelligent algorithms to analyze the content and accurately divide it into paragraphs, ensuring the completeness of the paragraphs for easy learning and processing by the model.

3. **Spreadsheet File Processing**:
    - When processing regular spreadsheet files, LinkMind will identify the header's position and layout, converting the content into Markdown format to optimize the model's learning and processing.

4. **Pure Numeric Spreadsheet File Processing**:
    - For spreadsheet files containing pure numbers, LinkMind will provide the optimal table slicing solution based on the types of numeric data in the table. It will use text2sql technology to convert the table content into structured data and import it into a MySQL database, where sql2text technology will enable intelligent querying. If MySQL is not configured, it will follow the "Spreadsheet File Processing" procedure.

5. **Image and Text File Processing**:
    - For files containing both text and images, LinkMind will integrate image-text layout technology to accurately extract images and content from the document, assisting the large model in learning and processing the file. If image-text layout is not configured, the file will be processed using the standard file processing procedure.

6. **Title File Processing**:
    - Titles in files will be extracted separately as key information units for specialized processing. Accurate recognition of titles ensures they are effectively extracted as core elements of the content, providing high-quality learning data for the large model.

7. **Presentation File Processing**:
    - For presentation files, LinkMind will read the content of each page, associating the page text with images to improve the large model's ability to learn and process the content.

8. **Image File Processing**:
    - When processing image files, LinkMind will use OCR technology to perform text and image recognition, associating the recognized information with the image as key information units. If OCR is not configured, the default method will be to associate the image name with the image for processing.

## Conclusion

By following this tutorial, you have successfully integrated LinkMind into your project. You can now start leveraging LinkMind's powerful AI features to enhance user experience and improve efficiency.

**Note 1**: Tutorial for installing idk1.8

1. **Download and install jdk1.8**: jdk1.8 download address: https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html

2. **Configure jdk environment variables**:
Search for ‘Edit system environment variables’ to enter the system environment variables and add the corresponding environment variables for jdk.
* Add JAVA_HOME variable: Click New, in the New dialog box that pops up, add the following content:
Variable name: JAVA_HOME
Variable value: C:\Program Files\java\jdk
* Add CLASSPATH variable: Click New, in the New dialog box that pops up, add the following content:
Variable name: CLASSPATH
Variable value: .;%JAVA_HOME%\lib\dt.jar;%JAVA_HOME%\lib\tools.jar
* Change Path variable: Click Edit, click New in the New dialog box that pops up, add %JAVA_HOME%\bin;%JAVA_HOME%\jre\bin.
* Test whether jdk configuration is complete, win+R opens a black window, and enter cmd
Use the java -version command to check the java version. If the jdk version number is displayed, the installation and configuration are complete
```bash
java -version
```
**Note 2**: Tutorial for installing Tomcat

**Download Tomcat**: Tomcat download address: https://archive.apache.org/dist/tomcat/tomcat-8/v8.5.99/bin/apache-tomcat-8.5.99.zip

**Note 3**: Tutorial on installing Python

**Download Python**: Python official website: https://www.python.org/