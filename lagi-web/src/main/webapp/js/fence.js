let fenceData = [];
let fenceRefreshInterval = null;

let currentPage = 1;
let pageSize = 20;
let totalPages = 1;

function loadFencePage() {
    $('#queryBox').hide();
    $('#footer-info').hide();
    $('#introduces').hide();
    $('#topTitle').hide();
    $('#item-content').show();
    $('#item-content').css('height', 'calc(100vh - 60px)');
    $('#item-content').css('overflow-y', 'auto');
    hideBallDiv();
    if (fenceRefreshInterval) {
        clearInterval(fenceRefreshInterval);
        fenceRefreshInterval = null;
    }
    autoRefreshEnabled = false;
    currentPage = 1;
    const html = `
        <div id="fence-container" style="padding: 20px; height: 100%; display: flex; flex-direction: column;">
            <div style="margin-bottom: 20px;">
                <h2 style="margin-bottom: 10px;">电子围栏监控</h2>
                <button id="refreshBtn" onclick="refreshFenceData()" style="padding: 8px 16px; background: #1296db; color: white; border: none; border-radius: 4px; cursor: pointer;">刷新</button>
                <button id="autoRefreshBtn" onclick="toggleAutoRefresh()" style="padding: 8px 16px; background: #1296db; color: white; border: none; border-radius: 4px; cursor: pointer; margin-left: 10px;">自动刷新: 关闭</button>
            </div>
            <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 20px; margin-bottom: 20px;">
                <div style="background: white; border-radius: 8px; padding: 16px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                    <div style="font-size: 14px; color: #666; margin-bottom: 8px;">总拦截次数</div>
                    <div id="totalBlockCount" style="font-size: 32px; font-weight: bold; color: #1296db;">0</div>
                </div>
                <div style="background: white; border-radius: 8px; padding: 16px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                    <div style="font-size: 14px; color: #666; margin-bottom: 8px;">今日拦截</div>
                    <div id="todayBlockCount" style="font-size: 32px; font-weight: bold; color: #1296db;">0</div>
                </div>
                <div style="background: white; border-radius: 8px; padding: 16px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                    <div style="font-size: 14px; color: #666; margin-bottom: 8px;">近1小时拦截</div>
                    <div id="hourBlockCount" style="font-size: 32px; font-weight: bold; color: #1296db;">0</div>
                </div>
            </div>
            <div style="background: white; border-radius: 8px; padding: 16px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); margin-bottom: 20px; flex-shrink: 0;">
                <div style="margin-bottom: 16px; font-size: 16px; font-weight: bold;">拦截趋势图（近24小时）</div>
                <div id="fenceChart" style="height: 250px;"></div>
                <div id="chartNoData" style="display: none; text-align: center; padding: 60px; color: #999;">暂无数据</div>
            </div>
            <div style="background: white; border-radius: 8px; padding: 16px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); flex: 1; display: flex; flex-direction: column; min-height: 0; overflow: hidden;">
                <div style="margin-bottom: 12px; font-size: 16px; font-weight: bold; flex-shrink: 0;">监控日志</div>
                <div style="flex: 1; overflow-y: auto; min-height: 0;">
                    <table id="fenceTable" style="width: 100%; border-collapse: collapse;">
                        <thead style="position: sticky; top: 0; background: #f5f5f5; z-index: 10;">
                            <tr style="background: #f5f5f5;">
                                <th style="padding: 12px; text-align: left; border-bottom: 1px solid #ddd;">时间</th>
                                <th style="padding: 12px; text-align: left; border-bottom: 1px solid #ddd;">过滤器</th>
                                <th style="padding: 12px; text-align: left; border-bottom: 1px solid #ddd;">操作类型</th>
                                <th style="padding: 12px; text-align: left; border-bottom: 1px solid #ddd;">内容</th>
                            </tr>
                        </thead>
                        <tbody id="fenceTableBody">
                        </tbody>
                    </table>
                </div>
                <div id="fencePagination" style="margin-top: 8px; padding-top: 8px; border-top: 1px solid #eee; display: flex; justify-content: center; align-items: center; gap: 10px; flex-shrink: 0;">
                    <button id="prevPageBtn" onclick="changePage(-1)" style="padding: 6px 12px; background: #1296db; color: white; border: none; border-radius: 4px; cursor: pointer;" disabled>上一页</button>
                    <span id="pageInfo" style="padding: 0 16px;">第 1 页 / 共 1 页</span>
                    <button id="nextPageBtn" onclick="changePage(1)" style="padding: 6px 12px; background: #1296db; color: white; border: none; border-radius: 4px; cursor: pointer;" disabled>下一页</button>
                </div>
            </div>
        </div>
    `;
    $('#item-content').html(html);
    refreshFenceData();
}

function refreshFenceData() {
    $.ajax({
        type: "GET",
        contentType: "application/json;charset=utf-8",
        url: "fence/list",
        timeout: 10000,
        success: function(response) {
            if (response && response.code === 0) {
                fenceData = response.data || [];
                updateFenceDisplay();
            } else {
                console.warn("获取监控数据失败:", response);
                fenceData = [];
                updateFenceDisplay();
            }
        },
        error: function(xhr, status, error) {
            console.error("获取监控数据失败:", status, error);
            fenceData = [];
            updateFenceDisplay();
        }
    });
    
    $.ajax({
        type: "GET",
        contentType: "application/json;charset=utf-8",
        url: "fence/stats",
        timeout: 10000,
        success: function(response) {
            if (response && response.code === 0) {
                const stats = response.data;
                $('#totalBlockCount').text(stats.total || 0);
                $('#todayBlockCount').text(stats.today || 0);
                $('#hourBlockCount').text(stats.hour || 0);
            }
        },
        error: function(xhr, status, error) {
            console.error("获取统计数据失败:", status, error);
        }
    });
}

function updateFenceDisplay() {
    const tbody = $('#fenceTableBody');
    tbody.empty();
    
    if (fenceData.length === 0) {
        tbody.append('<tr><td colspan="4" style="padding: 40px; text-align: center; color: #999;">暂无监控数据</td></tr>');
        $('#fencePagination').hide();
        renderChart();
        return;
    }
    
    totalPages = Math.ceil(fenceData.length / pageSize);
    const startIndex = (currentPage - 1) * pageSize;
    const endIndex = startIndex + pageSize;
    const pageData = fenceData.slice(startIndex, endIndex);
    
    pageData.forEach(item => {
        const row = `
            <tr style="border-bottom: 1px solid #eee;">
                <td style="padding: 12px;">${item.create_time || ''}</td>
                <td style="padding: 12px;">${item.filter_name || ''}</td>
                <td style="padding: 12px;">${item.action_type || ''}</td>
                <td style="padding: 12px; max-width: 400px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" title="${item.content || ''}">${item.content || ''}</td>
            </tr>
        `;
        tbody.append(row);
    });
    
    updatePagination();
    renderChart();
}

function updatePagination() {
    $('#pageInfo').text(`第 ${currentPage} 页 / 共 ${totalPages} 页`);
    $('#prevPageBtn').prop('disabled', currentPage <= 1);
    $('#nextPageBtn').prop('disabled', currentPage >= totalPages);
    if (totalPages <= 1) {
        $('#fencePagination').hide();
    } else {
        $('#fencePagination').show();
    }
}

function changePage(delta) {
    const newPage = currentPage + delta;
    if (newPage >= 1 && newPage <= totalPages) {
        currentPage = newPage;
        updateFenceDisplay();
        const tbody = document.getElementById('fenceTableBody');
        if (tbody) {
            tbody.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    }
}

function renderChart() {
    const chartDom = document.getElementById('fenceChart');
    const noDataDiv = document.getElementById('chartNoData');
    if (!chartDom || typeof echarts === 'undefined') {
        return;
    }
    
    const now = new Date();
    const hours = [];
    const counts = [];
    let hasData = false;
    
    for (let i = 23; i >= 0; i--) {
        const hourTime = new Date(now.getTime() - i * 60 * 60 * 1000);
        const hourStr = hourTime.getHours() + ':00';
        hours.push(hourStr);
        
        const hourStart = new Date(hourTime);
        hourStart.setMinutes(0);
        hourStart.setSeconds(0);
        hourStart.setMilliseconds(0);
        const hourEnd = new Date(hourStart.getTime() + 60 * 60 * 1000);
        
        const count = fenceData.filter(item => {
            if (!item.create_time) return false;
            const itemTime = new Date(item.create_time);
            return itemTime >= hourStart && itemTime < hourEnd;
        }).length;
        
        if (count > 0) hasData = true;
        counts.push(count);
    }
    
    if (!hasData) {
        chartDom.style.display = 'none';
        if (noDataDiv) noDataDiv.style.display = 'block';
        return;
    }
    
    chartDom.style.display = 'block';
    if (noDataDiv) noDataDiv.style.display = 'none';
    
    const chart = echarts.init(chartDom);
    const option = {
        tooltip: {
            trigger: 'axis'
        },
        xAxis: {
            type: 'category',
            data: hours
        },
        yAxis: {
            type: 'value'
        },
        series: [{
            data: counts,
            type: 'line',
            smooth: true,
            areaStyle: {
                opacity: 0.3
            },
            itemStyle: {
                color: '#1296db'
            }
        }]
    };
    chart.setOption(option);
    
    const resizeHandler = function() {
        chart.resize();
    };
    window.removeEventListener('resize', resizeHandler);
    window.addEventListener('resize', resizeHandler);
}

let autoRefreshEnabled = false;

function toggleAutoRefresh() {
    autoRefreshEnabled = !autoRefreshEnabled;
    const btn = $('#autoRefreshBtn');
    if (autoRefreshEnabled) {
        btn.text('自动刷新: 开启');
        fenceRefreshInterval = setInterval(refreshFenceData, 5000);
    } else {
        btn.text('自动刷新: 关闭');
        if (fenceRefreshInterval) {
            clearInterval(fenceRefreshInterval);
            fenceRefreshInterval = null;
        }
    }
}

