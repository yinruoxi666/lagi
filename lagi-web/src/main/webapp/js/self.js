// mobile debug console
// @ 调试插件
// var vConsole = new window.VConsole();
var lastFilePath = "";
let mediaRecorder = null;
let audioData = [];

// 播放录音
function playRecording() {
    if (audioData instanceof Blob) {
        const audio = new Audio(URL.createObjectURL(audioData));
        audio.play();
    }
}

// window.onload = function () {
const voiceButton = document.getElementById("voiceIcon");

// 按钮长按开始录音
voiceButton.addEventListener("mousedown", (e) => {
    timeOutEvent = setTimeout(() => {
        longPress()
    }, 300);
    e.preventDefault();
});

voiceButton.addEventListener("mousemove", (e) => {
    clearTimeout(timeOutEvent);
    timeOutEvent = 0;
});

voiceButton.addEventListener("mouseout", () => {
    clearTimeout(timeOutEvent);
    Recoder.stop();
});

// 松开按钮停止录音
voiceButton.addEventListener("mouseup", () => {
    clearTimeout(timeOutEvent);
    Recoder.stop();
});

var timeOutEvent = 0;
$(function () {
    $("#voiceIcon").on({
        touchstart: function (e) {
            longPress();
        },
        touchmove: function () {
            clearTimeout(timeOutEvent);
            timeOutEvent = 0;
        },
        touchend: function () {
            clearTimeout(timeOutEvent);
            if (timeOutEvent != 0) {
                alert("你这是点击，不是长按");
            }
            Recoder.stop();
            return false;
        }
    })
});

function longPress() {
    timeOutEvent = 0;
    Recoder.start();
}


//  转发功能的实现

const forwardButton = document.getElementById("forwardButton");

// 点击事件处理程序
forwardButton.addEventListener("click", () => {
    // 获取当前的IP地址和端口号，这里假设你已经有一个函数来获取它
    const ipAddressAndPort = getCurrentIpAddressAndPort();

    // 创建一个临时的textarea元素
    const tempTextarea = document.createElement("textarea");
    tempTextarea.value = ipAddressAndPort;

    // 将textarea元素添加到DOM中
    document.body.appendChild(tempTextarea);

    // 选中textarea中的文本
    tempTextarea.select();
    tempTextarea.setSelectionRange(0, 99999); // 选中所有文本，兼容不同浏览器

    // 复制选中的文本到剪贴板
    document.execCommand("copy");

    // 从DOM中移除临时的textarea元素
    document.body.removeChild(tempTextarea);

    // 提示用户复制成功
    alert("已复制到剪贴板：" + ipAddressAndPort);
});

// 获取当前的IP地址和端口号的示例函数
function getCurrentIpAddressAndPort() {
    const currentUrl = window.location.href;
    // 获取currentUrl的主机地址
    const currentHost = new URL(currentUrl).host;
    return currentUrl;
}

var voice_url = '';

// 播音功能的实现
function txtTovoice(txt, emotion) {
    // 获取最后一个进行播放。
    var len = $(".myAudio1").length;
// 检查是否至少存在一个匹配的元素
    if (len > 0) {
        // 要发送的 JSON 数据
        const postData = {
            "model": "default",
            "emotion": emotion,
            "text": txt,
            "category": window.category
        };

        // 创建 XMLHttpRequest 对象
        const xhr = new XMLHttpRequest();

        // 配置请求
        xhr.open('POST', '/audio/text2Voice', true);
        xhr.setRequestHeader('Content-Type', 'application/json');

        // 设置响应处理函数
        xhr.onload = function () {
            if (xhr.status === 200) {
                // 请求成功，处理响应数据
                const responseText = xhr.responseText;
                var json = JSON.parse(responseText);
                if (json.status == "success") {
                    console.log(json.data);

                    voice_url = json.data;
                    // const audioElement = document.getElementsByClassName('myAudio1')[len - 1];
                    $(".myAudio1")[len-  1].src = json.data
                    // const playButton = document.getElementsByClassName('playIcon1')[len - 1];
                    // const audioSource = document.getElementById("audioSource");
                    const audioplay =  document.getElementsByClassName('audioplay')[len-1];
                    audioplay.removeAttribute('disabled');
                    audioplay.innerHTML = AUDIO_PLAY_ICON
                }
                console.log('响应数据:', responseText);
            } else {
                // 请求失败，处理错误
                console.error('请求失败，状态码:', xhr.status);
            }
        };

        xhr.onerror = function () {
            // 发生网络错误
            console.error('网络错误');
        };

        // 发送 POST 请求并将 JSON 数据转换为字符串
        xhr.send(JSON.stringify(postData));

    } else {
        alert("当前无内容播放，请先输入对答");
    }

}

const WORD_FILE_SIZE_LIMIT = 5 * 1024 * 1024;
const TXT_FILE_SIZE_LIMIT = 5 * 1024 * 1024;
const PDF_FILE_SIZE_LIMIT = 5 * 1024 * 1024;
const EXCEL_FILE_SIZE_LIMIT = 5 * 1024 * 1024;
const PPT_FILE_SIZE_LIMIT = 5 * 1024 * 1024;
const IMAGE_FILE_SIZE_LIMIT = 10 * 1024 * 1024;
const AUDIO_FILE_SIZE_LIMIT = 2 * 1024 * 1024;
const VIDEO_FILE_SIZE_LIMIT = 50 * 1024 * 1024;

const FILE_SIZE_LIMIT = {
    "pdf": PDF_FILE_SIZE_LIMIT,
    "doc": WORD_FILE_SIZE_LIMIT,
    "docx": WORD_FILE_SIZE_LIMIT,
    "txt": TXT_FILE_SIZE_LIMIT,
    "csv": EXCEL_FILE_SIZE_LIMIT,
    "xlsx": EXCEL_FILE_SIZE_LIMIT,
    "xls": EXCEL_FILE_SIZE_LIMIT,
    "ppt": PPT_FILE_SIZE_LIMIT,
    "pptx": PPT_FILE_SIZE_LIMIT,
    "jpg": IMAGE_FILE_SIZE_LIMIT,
    "jpeg": IMAGE_FILE_SIZE_LIMIT,
    "png": IMAGE_FILE_SIZE_LIMIT,
    "heic": IMAGE_FILE_SIZE_LIMIT,
    "mp3": AUDIO_FILE_SIZE_LIMIT,
    "wav": AUDIO_FILE_SIZE_LIMIT,
    "pcm": AUDIO_FILE_SIZE_LIMIT,
    "mp4": VIDEO_FILE_SIZE_LIMIT,
    "avi": VIDEO_FILE_SIZE_LIMIT
};

function checkFileSizeLimit(selectedFile) {
    let fileType = selectedFile.name.split('.').pop();
    fileType = fileType.toLowerCase();
    var fileSize = selectedFile.size;
    for (var type in FILE_SIZE_LIMIT) {
        if (fileType === type && fileSize < FILE_SIZE_LIMIT[type]) {
            return true;
        }
    }
    return false;
}

let conversation1 = null;


var voiceResponse = "";

// 绑定文件上传按钮点击事件
const fileUploadButton = document.getElementById("addButton");
fileUploadButton.addEventListener("click", function () {
    const fileInput = document.createElement("input");
    fileInput.type = "file";
    fileInput.accept = ".pdf, .doc, .docx, .txt, .csv, .xlsx, .xls, .ppt, .pptx, .jpg, .jpeg, .png, .heic, .mp3, .wav, .avi , .mp4, .pcm";
    fileInput.style.display = "none";

    // 将文件输入元素添加到页面
    document.body.appendChild(fileInput);

    // 模拟点击文件输入元素
    fileInput.click();

    // 监听文件选择事件
    fileInput.addEventListener("change", function () {
        disableQueryBtn();

        const selectedFile = fileInput.files[0];
        if (selectedFile) {
            // 获取文件类型
            let fileType = selectedFile.name.split('.').pop();
            fileType = fileType.toLowerCase();

            // 创建 FormData 对象
            const formData = new FormData();

            var question = "";
            // 设置服务器端点URL
            let serverEndpoint = "";
            var fileStatus = "";
            if (fileType === "pdf" || fileType === "doc" || fileType === "docx" || fileType === "txt" ||
                fileType === "xls" || fileType === "xlsx" || fileType === "ppt" || fileType === "pptx"||
                fileType === "csv") {
                question = "您所上传的文档文件名称为：" + selectedFile.name;
                formData.append("file", selectedFile); // 使用 "file" 作为文件字段的名称
                serverEndpoint = "/uploadFile/uploadLearningFile?category=" + window.category;
                fileStatus = "doc";
            } else if (fileType === "jpg" || fileType === "jpeg" || fileType === "png" || fileType === "heic") {
                question = "您所上传的图片是：" + selectedFile.name;
                serverEndpoint = "/uploadFile/uploadImageFile";
                formData.append("file", selectedFile);
                fileStatus = "pic";
            } else if (fileType === "mp3" || fileType === "wav" || fileType === "pcm") {
                question = "您所上传的音频文件名称为：" + selectedFile.name;
                formData.append("files", selectedFile);
                formData.append("category", "selectedFile");
                formData.append("total_epoch", 20);
                formData.append("batch_size", 4);
                serverEndpoint = "/audio/audioTrain";
                fileStatus = "voice";
            } else if (fileType === "avi" || fileType === "mp4") {
                formData.append("file", selectedFile);
                question = "您所上传的视频解析为：";
                serverEndpoint = "/uploadFile/uploadVideoFile";
                formData.append("file", selectedFile);
                fileStatus = "video";
            }

            if (!checkFileSizeLimit(selectedFile)) {
                // hideHelloContent();
                let conversation1 = {
                    user: {question: question},
                    robot: {answer: '鉴于当前资源有限，请适当缩减文件大小，敬请您的谅解！'}
                }
                newConversation(conversation1);
                addConv(conversation1);
                return;
            }

            if (fileStatus == "") {
                alert("请选择指定的文件类型")
                return;
            }
            // hideHelloContent();

            let conversation1 = {user: {question: question}, robot: {answer: ''}}
            let robootAnswerJq1 = newConversation(conversation1);


            // 发送 AJAX 请求
            const xhr = new XMLHttpRequest();
            xhr.open("POST", serverEndpoint, true);
            xhr.setRequestHeader("X-Requested-With", "XMLHttpRequest"); // 标识为 AJAX 请求
            xhr.onreadystatechange = function () {
                if (xhr.readyState === 4) {
                    if (xhr.status === 200) {
                        // 请求成功，可以在这里处理服务器的响应
                        responseText = xhr.responseText;
                        if (responseText != "") {
                            var json = JSON.parse(responseText);
                            if (fileStatus == 'pic') {
                                if (json.status === "success") {
                                    var question = "您所上传的图片名称为：" + selectedFile.name;
                                    var result = "已经收到您上传的图片。如果您想生成视频，请输入\"视频生成\"。" +
                                        "如果您想增强图片，请输入\"图像增强\"。如果您想使用AI描述图片，请输入\"看图说话\"。";
                                    lastFilePath = json.filePath;
                                    textQuery1(question, result, fileStatus);
                                } else {
                                    alert("上传失败");
                                    return;
                                }
                            } else if (fileStatus == "doc") {
                                if (json.status == "success") {

                                    // var question = "您所上传的文档文件名称为：" + selectedFile.name;
                                    // var result = "已经收到您的资料文档，您可以在新的会话中，询问与资料中内容相关的问题。"
                                    // textQuery1(question, result, fileStatus);
                                    var question = "您所上传的文档文件名称为：" + selectedFile.name;
                                    var result = "已经收到您的资料文档，您可以在新的会话中，询问与资料中内容相关的问题。如果您想生成指令集，请输入\"生成指令集\"。"
                                    lastFilePath = json.filePath;
                                    console.log("文件为" + lastFilePath)
                                    textQuery1(question, result, fileStatus);
                                } else {
                                    alert("上传失败")
                                    return;
                                }
                            } else if (fileStatus == "voice") {
                                if (json.status == "success") {
                                    var question = "您所上传的音频文件名称为：" + selectedFile.name;
                                    var voiceToTxtResult = voiceToTxt(selectedFile);
                                    var voiceResult = "";
                                    if (voiceToTxtResult != '') {
                                        var voiceToTxtJson = JSON.parse(voiceResponse);

                                        voiceResult = voiceToTxtJson.msg;

                                    }
                                    var result = `
                    已将您的语音素材用于训练声音，稍后积累足够时间，可以模仿您所提供的口音发声。<br><br>
                    你发送的音频文件的内容为： ${voiceResult}
                    `
                                    textQuery1(question, result, fileStatus);
                                } else {
                                    alert("上传失败");
                                    return;
                                }
                            } else if (fileStatus == "video") {
                                if (json.status == "success") {
                                    var question = "您所上传的视频文件名称为：" + selectedFile.name;
                                    var result = "已经收到您上传的视频。如果您想视频追踪，请输入\"视频追踪\"。" +
                                        "如果您想视频增强，请输入\"视频增强\"。";
                                    lastFilePath = json.filePath;
                                    textQuery1(question, result, fileStatus);
                                } else {
                                    alert("视频解析失败")
                                    return;
                                }
                            }
                        }
                    } else {
                        textQuery1("文件上传结果", "上传文件失败", "doc");
                        return;
                    }
                }
            };

            // 发送 FormData
            xhr.send(formData);
        } else {
            console.log("没有选中文件");
        }
        // 移除文件输入元素
        document.body.removeChild(fileInput);
    });
});


function textQuery1(questionRel, answerRel, fileStatus) {
    if (queryLock) {
        alert("有对话正在进行请耐心等待");
        return;
    }
    let question = questionRel;
    if (isBlank(question)) {
        alert("请输入有效字符串！！！");
        $('#queryBox textarea').val('');
        return;
    }
    queryLock = true;
    disableQueryBtn();

    // 隐藏非对话内容
    // hideHelloContent();

    const markdownElements = document.querySelectorAll(".markdown");
    var len = markdownElements.length;
    conversation1 = {user: {question: question}, robot: {answer: answerRel}};
    $(".markdown")[len - 1].innerHTML = answerRel;

    answerRel = answerRel.replace(/<[^>]*>/g, "")
    answerRel = answerRel.replaceAll("分割后的图片：", "")
    // 增加不需要音频文件的判断
    if (fileStatus != 'video') {
        txtTovoice(answerRel, "default");
    }
    addConv(conversation1);
    $('#queryBox textarea').val('');
    enableQueryBtn();
    querying = false;
    queryLock = false;
}


async function voiceToTxt(selectedFile) {
    return new Promise((resolve, reject) => {
        const formData = new FormData();
        const audioDataPost = new Blob([selectedFile], {
            type: 'audio/*'
        });
        formData.append('audioFile', audioDataPost, selectedFile.name);

        const xhr = new XMLHttpRequest();
        xhr.open('POST', '/search/uploadVoice', false); // 使用false表示同步请求
        xhr.setRequestHeader('Access-Control-Allow-Origin', 'localhost');
        xhr.setRequestHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
        xhr.setRequestHeader('Access-Control-Allow-Headers', 'Content-Type');
        xhr.setRequestHeader('Access-Control-Allow-Credentials', 'true');

        xhr.onload = function () {
            if (xhr.status === 200) {
                const responseText = xhr.responseText;
                voiceResponse = responseText
                resolve(responseText); // 上传成功时解决Promise
            } else {
                voiceResponse = "";
                reject(new Error("上传失败")); // 上传失败时拒绝Promise
            }
        };

        xhr.send(formData);
    });
}

function textToVoice(emotionSelect) {
    
    let text = CONVERSATION_CONTEXT[ $(emotionSelect).closest('.robot-return').data('index')].content.replace(/<think>[\s\S]*?<\/think>/g, '').trim();
    let emotion = $(emotionSelect).find("option:selected").val();

    // const audioElement = $(emotionSelect).parent().find('.myAudio1')[0];
    // const playButton = $(emotionSelect).parent().find('.playIcon1')[0];

    const audioElement = $(emotionSelect).closest('.appendVoice').find('.myAudio1')[0];
    const audioplay = $(emotionSelect).closest('.appendVoice').find('.audioplay')[0];
    audioplay.innerHTML = MUTE_ICON;
    audioplay.disabled = true;

    // const audioSource = $(emotionSelect).parent().find("audioSource1")[0];
    audioElement.src = "";

    $.ajax({
        type: "POST",
        url: "audio/text2Voice",
        data: JSON.stringify({
            "model": "default",
            "emotion": emotion,
            "text": text,
            "category": window.category
        }),
        success: function (res) {
            if (res.status == "success") {
                console.log(res.data);
                audioElement.src = res.data;
                audioElement.load();
                audioplay.disabled = false;
                audioplay.innerHTML = AUDIO_PLAY_ICON;
                // 添加点击事件处理程序来控制音频的播放和暂停
                // playButton.addEventListener('click', function () {
                //     console.log("点击了")
                //     if (audioElement.paused) {
                //         // 如果音频暂停，播放音频
                //         audioElement.play();

                //     } else {
                //         // 如果音频正在播放，暂停音频
                //         audioElement.pause();

                //     }
                // });
            }
        },
        error: function (res) {

        }
    });
    return;
}


const record_save_type = 'audio/mp3';

$(document).on("change", ".emotionSelect", function () {
    textToVoice(this);
})

function remoteSolve(blob) {
    const formData = new FormData();
    // 将MP3音频文件添加到FormData对象
    formData.append('audioFile', blob, 'audiofile.wav');
    const xhr = new XMLHttpRequest();
    xhr.open('POST', '/search/uploadVoice', true);
    xhr.setRequestHeader('Access-Control-Allow-Origin', 'localhost');
    xhr.setRequestHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
    xhr.setRequestHeader('Access-Control-Allow-Headers', 'Content-Type');
    xhr.setRequestHeader('Access-Control-Allow-Credentials', 'true');
    xhr.onload = function () {
        if (xhr.status === 200) {
            console.log('上传成功');
            const responseText = xhr.responseText;
            // 这是 返回的结果
            if (responseText != "") {
                try {
                    var json = JSON.parse(responseText);
                    if (json.code == 0) {
                        console.log(json.msg)
                        $('#queryBox textarea').val(json.msg);
                    }
                } catch {
                    alert("转换失败");
                }
            } else {
                alert("转换失败");
            }
            console.log(responseText);
        } else {

            alert("转换失败");
        }
    };
    xhr.send(formData);
    console.log(audioData);
}


// 新录音
var Recoder = {
    start() {
        navigator.permissions.query(
            {name: 'microphone'}
        ).then(function (permissionStatus) {
            if (permissionStatus.state !== 'prompt') {
                $("#voiceIcon").css("background", "rgb(200, 200, 200)");
                // 停止之前的录制内容
                mediaRecorder && mediaRecorder.stop();

                if (!window.AudioContext) {
                    window.AudioContext = window.AudioContext || window.webkitAudioContext || window.mozAudioContext || window.msAudioContext;
                }

                // 老版浏览器可能根本没有实现mediaDevices, 为其设置一个空对象
                if (!navigator.mediaDevices) {
                    navigator.mediaDevices = {};
                }
                if (!navigator.mediaDevices.getUserMedia) {
                    navigator.mediaDevices.getUserMedia = function (constraints) {
                        let getUserMedia = navigator.getUserMedia || navigator.webkitGetUserMedia || navigator.mozGetUserMedia || navigator.msGetUserMedia;
                        // 浏览器不支持
                        if (!getUserMedia) {
                            return Promise.reject(new Error('您的浏览器暂不支持 getUserMedia !'));
                        }
                        // 包装老版 getUserMedia
                        return new Promise((resolve, reject) => getUserMedia.call(navigator, constraints, resolve, reject));
                    }
                }
                // 参数
                // let constraints = {audio: true, video: true}; // 请求不带任何参数的音频和视频
                let constraints = {audio: true};  // 请求音频

                let mediaStream = navigator.mediaDevices.getUserMedia(constraints);

                mediaStream.then((stream) => {
                    console.info("打开成功!")

                    let audioContext = new AudioContext();

                    // 创建一个新的音视频对象
                    let destination = audioContext.createMediaStreamDestination();
                    // 创建音视频源
                    let mediaStreamSource = audioContext.createMediaStreamSource(stream);
                    // 将音视频源 链接 到新音视频对象 中
                    mediaStreamSource.connect(destination);
                    // 媒体录制接口
                    //   mimeType: "audio/webm"
                    let _mediaRecorder = new MediaRecorder(destination.stream, {audioBitsPerSecond: 16000});

                    let chunks = [];
                    // 有可用数据流时触发，e.data即需要的音视频数据
                    _mediaRecorder.ondataavailable = (e) => chunks.push(e.data);
                    // 间视频录制结束时触发
                    _mediaRecorder.onstop = () => {
                        // 通过Blob数据块, 合成完整的Blob块数据
                        let blob = new Blob(chunks, {'type': 'audio/wav'});
                        console.log(blob);

                        remoteSolve(blob);

                    };
                    // 將 mediaRecorder 对象扔到全局this中, 用于其他方法调用
                    mediaRecorder = _mediaRecorder;
                    // 录制开始
                    _mediaRecorder.start();
                }, () => {
                    $("#voiceIcon").css("background", "");
                    console.log("打开失败!");
                });
            } else {
                navigator.mediaDevices.getUserMedia = navigator.mediaDevices.getUserMedia || navigator.getUserMedia || navigator.webkitGetUserMedia || navigator.mozGetUserMedia;
                if (navigator.mediaDevices.getUserMedia) {
                    console.log("支持");
                    navigator.mediaDevices.getUserMedia({
                        audio: true
                    })
                        .then()
                        .catch((info) => {
                            $("#voiceIcon").css("background", "");
                            alert("无法获取麦克风权限！错误信息：" + info);
                        });
                } else {
                    alert("无法获取麦克风权限");
                }
            }
            permissionStatus.onchange = function () {
                console.log("Permission changed to " + this.state);
            }
        })
    },

    stop() {
        if (!mediaRecorder) {
            return;
        }
        // 返回录制的二进制数据, 调用这个方法后会生成一个新的Blob对象
        $("#voiceIcon").css("background", "");
        mediaRecorder.requestData();
        // 停止录制
        mediaRecorder.stop();
        mediaRecorder = null;
    }
}

const modelButton = document.getElementById("categoryButton");
modelButton.addEventListener("click", function (e) {
    $('#category-container').toggle();
    $(document).one("click", function () {
        $("#category-container").hide();
    });
    event.stopPropagation();
});

const agentButton = document.getElementById("agentButton");
agentButton.addEventListener("click", function (e) {
    $('#agent-container').toggle();
    $(document).one("click", function(){
        $("#agent-container").hide();
    });
    e.stopPropagation();
});
