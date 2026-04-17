function sleep (time) {
    return new Promise((resolve) => setTimeout(resolve, time));
}

function isBlank(value){      
    return !value || !value.toString().trim() || /^[\s\b\0]+$/.test(value.toString());
 }

 function getUuid() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
        var r = (Math.random() * 16) | 0,
        v = c == 'x' ? r : (r & 0x3) | 0x8;
        return v.toString(16);
    });
}

function storageJson(uuid,json){
	jsonStr=JSON.stringify(json);
	sessionStorage.setItem(uuid, jsonStr);
}


// 函数防抖
function debounce(fun,wait=1500){
    let timeout = null
    return function(){
        if(timeout){//如果存在定时器就清空
            clearTimeout(timeout)
        }
        timeout=setTimeout(()=>{
            fun.apply(this,arguments)
        },wait)
    }
 
}

function alert(e){
    const tText = window.tText || ((s) => s);
    $("#msg").remove();
    const $alertBox = $("#alert-box");
    if (!$alertBox.parent().is("body")) {
        $alertBox.appendTo("body");
    }
    $alertBox.css({
        display: "flex",
        position: "fixed",
        left: "0",
        top: "0",
        right: "0",
        bottom: "0",
        transform: "none",
        background: "rgba(15,23,42,0.45)",
        zIndex: "3000",
        alignItems: "center",
        justifyContent: "center",
        backdropFilter: "blur(1px)",
        margin: "0",
        padding: "0",
        width: "auto",
        height: "auto"
    });
    $alertBox.append(
        '<div id="msg" class="msg" style="position:relative;top:auto;left:auto;margin-top:0;width:min(460px,92vw);background:#fff;border:1px solid #e5e7eb;border-radius:12px;box-shadow:0 12px 34px rgba(15,23,42,.2);overflow:hidden;">' +
        '<div id="msg_top" class="msg_top" style="display:flex;justify-content:space-between;align-items:center;padding:12px 14px;border-bottom:1px solid #e5e7eb;font-size:16px;font-weight:600;color:#111827;">' +
        tText('信息') +
        '<span class="msg_close" style="font-size:20px;line-height:1;cursor:pointer;color:#6b7280;">×</span></div>' +
        '<div id="msg_cont" class="msg_cont" style="padding:16px 14px;color:#374151;line-height:1.6;word-break:break-word;">' + tText(String(e)) + '</div>' +
        '<div style="padding:0 14px 14px;">' +
        '<button class="msg_clear" id="msg_clear" style="width:100%;height:38px;border:none;border-radius:8px;background:#6366f1;color:#fff;font-size:14px;cursor:pointer;">' + tText('确定') + '</button>' +
        '</div></div>'
    );
    $alertBox.off("click.alertBox").on("click.alertBox", function(event) {
        if (event.target && event.target.id === "alert-box") {
            $("#msg").remove();
            $("#alert-box").hide();
        }
    });
    $(".msg_close").off("click").on("click", function (){
        $("#msg").remove();
        $("#alert-box").hide();
    });

    $(".msg_clear").off("click").on("click", function (){
        $("#msg").remove();
        $("#alert-box").hide();
    });
}


function confirm(e){
    const tText = window.tText || ((s) => s);
    $("#confirm-msg").remove();
    const $confirmBox = $("#confirm-box");
    if (!$confirmBox.parent().is("body")) {
        $confirmBox.appendTo("body");
    }
    $confirmBox.css({
        display: "flex",
        position: "fixed",
        left: "0",
        top: "0",
        right: "0",
        bottom: "0",
        transform: "none",
        background: "rgba(15,23,42,0.45)",
        zIndex: "3000",
        alignItems: "center",
        justifyContent: "center",
        backdropFilter: "blur(1px)",
        margin: "0",
        padding: "0",
        width: "auto",
        height: "auto"
    });
    $confirmBox.append(
        '<div id="confirm-msg" class="msg msg-container" style="position:relative;top:auto;left:auto;margin-top:0;width:min(460px,92vw);background:#fff;border:1px solid #e5e7eb;border-radius:12px;box-shadow:0 12px 34px rgba(15,23,42,.2);overflow:hidden;">' +
        '<div id="msg_top" class="msg_top" style="display:flex;justify-content:space-between;align-items:center;padding:12px 14px;border-bottom:1px solid #e5e7eb;font-size:16px;font-weight:600;color:#111827;">' + tText('信息') +
        '<span class="msg_close" style="font-size:20px;line-height:1;cursor:pointer;color:#6b7280;">×</span></div>' +
        '<div id="msg_cont" class="msg_cont" style="padding:16px 14px;color:#374151;line-height:1.6;word-break:break-word;">' + tText(String(e)) + '</div>' +
        '<div style="display:flex;gap:10px;padding:0 14px 14px;">' +
        '<button id="msg_cancel" class="msg_cancel left" style="flex:1;height:38px;border:1px solid #d1d5db;border-radius:8px;background:#fff;color:#374151;font-size:14px;cursor:pointer;">' + tText('取消') + '</button>' +
        '<button class="msg_close right" id="msg_sure" style="flex:1;height:38px;border:none;border-radius:8px;background:#6366f1;color:#fff;font-size:14px;cursor:pointer;">' + tText('确定') + '</button>' +
        '</div></div>'
    );

    const confirmBox = document.getElementById("confirm-box");
    const closeBtn = document.getElementsByClassName('msg_close')[0];
    const confirmYes = document.getElementById('msg_sure');
    const confirmNo = document.getElementById('msg_cancel');
    // console.log("confirm", customConfirm, closeBtn, confirmYes, confirmNo);
    return new Promise((resolve) => {
        // 处理确定按钮点击
        confirmYes.onclick = function() {
            $("#confirm-msg").remove();
            $("#confirm-box").hide();
            resolve(true);
        }

        // 处理取消按钮点击
        confirmNo.onclick = function() {
            $("#confirm-msg").remove();
            $("#confirm-box").hide();
            resolve(false);
        }

        // 处理关闭按钮点击
        closeBtn.onclick = function() {
            $("#confirm-msg").remove();
            $("#confirm-box").hide();
            resolve(false);
        }

        // Close dialog when clicking on overlay only.
        $confirmBox.off("click.confirmBox").on("click.confirmBox", function(event) {
            if (event.target && event.target.id === "confirm-box") {
                $("#confirm-msg").remove();
                $("#confirm-box").hide();
                resolve(false);
            }
        });
    });

}


async function test() {
    let a =  await confirm("确定");
    console.log(a);
}

// let a = confirm('你好');

// const result = await confirm('你确定要执行此操作吗？');
// console.log(result);