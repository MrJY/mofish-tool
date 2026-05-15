package online.mofish.tool.ui.web

import online.mofish.tool.domain.FundQuote

object MoFishFundTrend {
    fun requestFor(quote: FundQuote): MoFishWebRequest {
        return MoFishWebRequest.Html(
            title = "摸鱼基金走势 - ${quote.name}",
            html = chartHtml(
                code = escapeHtml(quote.code),
                name = escapeHtml(quote.name),
            ),
        )
    }

    private fun chartHtml(code: String, name: String): String {
        val dataUrl = "https://fund.eastmoney.com/pingzhongdata/$code.js"
        val externalUrl = "https://fund.eastmoney.com/$code.html"
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8">
              <style>
                :root {
                  --bg: #f5f7fb;
                  --panel: #ffffff;
                  --border: #d9dee8;
                  --text: #20242c;
                  --muted: #687080;
                  --accent: #2f6fdd;
                  --accent-soft: rgba(47, 111, 221, 0.12);
                  --line: #3578d4;
                  --profit: #d4380d;
                  --loss: #138a36;
                }
                html, body {
                  width: 100%;
                  min-height: 100%;
                  margin: 0;
                  background: var(--bg);
                  color: var(--text);
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                }
                body {
                  box-sizing: border-box;
                  padding: 16px;
                }
                .topbar {
                  display: flex;
                  align-items: center;
                  justify-content: space-between;
                  gap: 16px;
                  margin-bottom: 14px;
                }
                .title {
                  min-width: 0;
                }
                h1 {
                  margin: 0;
                  font-size: 20px;
                  line-height: 1.3;
                }
                .sub {
                  margin-top: 4px;
                  color: var(--muted);
                  font-size: 12px;
                }
                .actions {
                  display: flex;
                  align-items: center;
                  gap: 8px;
                  flex-shrink: 0;
                }
                a, button {
                  height: 28px;
                  box-sizing: border-box;
                  border: 1px solid var(--border);
                  border-radius: 4px;
                  background: var(--panel);
                  color: var(--text);
                  cursor: pointer;
                  font-size: 12px;
                  line-height: 26px;
                  padding: 0 10px;
                  text-decoration: none;
                }
                a:hover, button:hover {
                  border-color: #9eb7e8;
                  color: var(--accent);
                  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.85), 0 1px 4px rgba(47, 111, 221, 0.16);
                }
                button.active {
                  border-color: var(--accent);
                  background: var(--accent-soft);
                  color: var(--accent);
                  font-weight: 600;
                }
                .grid {
                  display: grid;
                  grid-template-columns: minmax(0, 1fr);
                  gap: 14px;
                }
                .panel {
                  background: var(--panel);
                  border: 1px solid var(--border);
                  border-radius: 6px;
                  padding: 12px;
                }
                .panel-head {
                  display: flex;
                  align-items: center;
                  justify-content: space-between;
                  gap: 12px;
                  margin-bottom: 10px;
                }
                h2 {
                  margin: 0;
                  font-size: 15px;
                }
                .metric {
                  color: var(--muted);
                  font-size: 12px;
                }
                canvas {
                  display: block;
                  width: 100%;
                  height: 360px;
                }
                .chart-frame {
                  position: relative;
                }
                .legend {
                  display: flex;
                  align-items: center;
                  flex-wrap: wrap;
                  gap: 10px;
                  color: var(--muted);
                  font-size: 12px;
                }
                .legend-item {
                  display: inline-flex;
                  align-items: center;
                  gap: 5px;
                }
                .legend-dot {
                  width: 8px;
                  height: 8px;
                  border-radius: 50%;
                  flex: 0 0 auto;
                }
                .navigator {
                  height: 58px;
                  margin-top: -2px;
                  cursor: ew-resize;
                }
                .empty {
                  display: none;
                  padding: 48px 16px;
                  text-align: center;
                  color: var(--muted);
                  background: var(--panel);
                  border: 1px dashed var(--border);
                  border-radius: 6px;
                }
                body.loading .grid,
                body.error .grid {
                  display: none;
                }
                body.loading .empty.loading,
                body.error .empty.error {
                  display: block;
                }
              </style>
            </head>
            <body class="loading">
              <div class="topbar">
                <div class="title">
                  <h1>$name</h1>
                  <div class="sub">基金代码 $code · 数据源 东方财富 · <span id="updatedAt">加载中</span></div>
                </div>
                <div class="actions">
                  <button data-range="30">1月</button>
                  <button data-range="90" class="active">3月</button>
                  <button data-range="180">6月</button>
                  <button data-range="365">1年</button>
                  <button data-range="0">全部</button>
                  <a href="$externalUrl">外部浏览器打开</a>
                </div>
              </div>
              <div class="empty loading">正在加载基金走势数据...</div>
              <div class="empty error">基金走势数据加载失败，可以稍后重试或使用外部浏览器打开。</div>
              <div class="grid">
                <section class="panel">
                  <div class="panel-head">
                    <h2>单位净值走势</h2>
                    <div class="metric" id="netWorthMetric">--</div>
                  </div>
                  <div class="chart-frame">
                    <canvas id="netWorthCanvas"></canvas>
                    <canvas id="netWorthNavigator" class="navigator"></canvas>
                  </div>
                </section>
                <section class="panel">
                  <div class="panel-head">
                    <h2>累计收益率走势</h2>
                    <div class="metric" id="profitMetric">--</div>
                  </div>
                  <div class="legend" id="profitLegend"></div>
                  <canvas id="profitCanvas"></canvas>
                </section>
              </div>
              <script src="$dataUrl?v=${'$'}{Date.now()}"></script>
              <script>
                const state = {
                  rangeDays: 90,
                  hover: {},
                  navigator: { dragging: null, start: 0, end: 1 },
                  netWorth: Array.isArray(window.Data_netWorthTrend) ? window.Data_netWorthTrend : [],
                  profit: Array.isArray(window.Data_grandTotal) ? window.Data_grandTotal : [],
                  netWorthAll: [],
                  profitSeries: []
                };
                const dayMs = 24 * 60 * 60 * 1000;

                document.querySelectorAll('button[data-range]').forEach((button) => {
                  button.addEventListener('click', () => {
                    document.querySelectorAll('button[data-range]').forEach((item) => item.classList.remove('active'));
                    button.classList.add('active');
                    state.rangeDays = Number(button.dataset.range || 0);
                    applyRangeToNavigator();
                    render();
                  });
                });

                installHover(document.getElementById('netWorthCanvas'), 'netWorth');
                installHover(document.getElementById('profitCanvas'), 'profit');
                installNavigator(document.getElementById('netWorthNavigator'));

                function normalizeSeries(series) {
                  return series
                    .map((item) => {
                      if (Array.isArray(item)) {
                        return { x: Number(item[0]), y: Number(item[1]) };
                      }
                      return { x: Number(item.x), y: Number(item.y) };
                    })
                    .filter((item) => Number.isFinite(item.x) && Number.isFinite(item.y))
                    .sort((a, b) => a.x - b.x);
                }

                function normalizeProfitSeries(series) {
                  if (!series.length) {
                    return [];
                  }
                  const first = series[0];
                  const data = first && Array.isArray(first.data) ? first.data : series;
                  return normalizeSeries(data);
                }

                function createProfitSeries() {
                  const colors = ['#d4380d', '#2f6fdd', '#8a63d2'];
                  // Eastmoney's Data_grandTotal already carries the comparison lines used by the
                  // official fund page: fund itself, category average, and HS300. Keep that source
                  // as the primary path so the local canvas page stays visually clean without
                  // inventing benchmark data.
                  const result = Array.isArray(state.profit)
                    ? state.profit
                      .filter((item) => item && Array.isArray(item.data))
                      .map((item, index) => ({
                        name: index === 0 ? '本基金' : (item.name || ('对比' + index)),
                        color: colors[index] || '#687080',
                        data: normalizeSeries(item.data)
                      }))
                      .filter((series) => series.data.length)
                    : [];
                  if (!result.length) {
                    const fund = normalizeProfitSeries(state.profit);
                    if (fund.length) {
                      result.push({ name: '本基金', color: colors[0], data: fund });
                    }
                  }
                  const hasSimilar = result.some((series) => series.name.includes('同类'));
                  const hasHs300 = result.some((series) => series.name.toLowerCase().includes('300'));
                  // Older or unusual fund scripts may omit the named series wrapper. These legacy
                  // globals are used only as a fallback and are skipped when Data_grandTotal already
                  // supplied the comparison lines.
                  const similar = hasSimilar ? [] : pickWindowSeries(['Data_rateInSimilarPersent', 'Data_rateInSimilarType', 'Data_similarTypeTrend']);
                  if (similar.length) {
                    result.push({ name: '同类平均', color: colors[1], data: similar });
                  }
                  const hs300 = hasHs300 ? [] : pickWindowSeries(['Data_rateInHS300', 'Data_hs300Trend', 'Data_HS300']);
                  if (hs300.length) {
                    result.push({ name: '沪深300', color: colors[2], data: hs300 });
                  }
                  return result;
                }

                function pickWindowSeries(names) {
                  for (const name of names) {
                    if (Array.isArray(window[name])) {
                      const normalized = normalizeProfitSeries(window[name]);
                      if (normalized.length) {
                        return normalized;
                      }
                    }
                  }
                  return [];
                }

                function filterRange(series) {
                  if (series.length === 0) {
                    return series;
                  }
                  const first = series[0].x;
                  const last = series[series.length - 1].x;
                  const start = first + (last - first) * state.navigator.start;
                  const end = first + (last - first) * state.navigator.end;
                  return series.filter((point) => point.x >= start && point.x <= end);
                }

                function applyRangeToNavigator() {
                  const all = state.netWorthAll;
                  if (!all.length || !state.rangeDays) {
                    state.navigator.start = 0;
                    state.navigator.end = 1;
                    return;
                  }
                  const first = all[0].x;
                  const last = all[all.length - 1].x;
                  const min = Math.max(first, last - state.rangeDays * dayMs);
                  state.navigator.start = Math.max(0, Math.min(0.98, (min - first) / Math.max(1, last - first)));
                  state.navigator.end = 1;
                }

                function filterSeriesByBounds(series, start, end) {
                  if (!series.length) {
                    return [];
                  }
                  return series.filter((point) => point.x >= start && point.x <= end);
                }

                function render() {
                  state.netWorthAll = normalizeSeries(state.netWorth);
                  if (state.netWorthAll.length && state.navigator.end === 1 && state.navigator.start === 0 && state.rangeDays) {
                    applyRangeToNavigator();
                  }
                  state.profitSeries = createProfitSeries();
                  const netWorth = filterRange(state.netWorthAll);
                  const bounds = chartBounds(netWorth.length ? netWorth : state.netWorthAll);
                  const profitSeries = state.profitSeries
                    .map((series) => ({
                      name: series.name,
                      color: series.color,
                      data: bounds ? filterSeriesToVisibleRange(series.data, bounds.start, bounds.end) : series.data
                    }))
                    .filter((series) => series.data.length);
                  if (!netWorth.length && !profitSeries.length) {
                    document.body.className = 'error';
                    return;
                  }
                  document.body.className = '';
                  drawChart(
                    document.getElementById('netWorthCanvas'),
                    [{ name: '单位净值', color: '#3578d4', data: netWorth }],
                    { suffix: '', decimals: 4, fillFirst: true, hoverKey: 'netWorth' }
                  );
                  drawNavigator(document.getElementById('netWorthNavigator'), state.netWorthAll);
                  drawChart(
                    document.getElementById('profitCanvas'),
                    profitSeries,
                    { suffix: '%', decimals: 2, zeroLine: true, hoverKey: 'profit' }
                  );
                  updateLegend(profitSeries);
                  updateMetrics(netWorth, profitSeries);
                }

                function chartBounds(series) {
                  if (!series.length) {
                    return null;
                  }
                  return { start: series[0].x, end: series[series.length - 1].x };
                }

                function filterSeriesToVisibleRange(series, start, end) {
                  const visible = filterSeriesByBounds(series, start, end);
                  if (visible.length) {
                    return visible;
                  }
                  // Benchmark data can have a different date calendar from net value data. When a
                  // dragged range lands between benchmark points, keep the nearest two points so the
                  // line does not disappear while the user is narrowing the window.
                  const nearestStart = nearestPoint(series, start);
                  const nearestEnd = nearestPoint(series, end);
                  return nearestStart === nearestEnd ? [nearestStart] : [nearestStart, nearestEnd].sort((a, b) => a.x - b.x);
                }

                function updateLegend(seriesList) {
                  const legend = document.getElementById('profitLegend');
                  legend.innerHTML = '';
                  seriesList.forEach((series) => {
                    const item = document.createElement('span');
                    item.className = 'legend-item';
                    const dot = document.createElement('span');
                    dot.className = 'legend-dot';
                    dot.style.background = series.color;
                    item.appendChild(dot);
                    item.appendChild(document.createTextNode(series.name));
                    legend.appendChild(item);
                  });
                }

                function updateMetrics(netWorth, profitSeries) {
                  const netLast = netWorth[netWorth.length - 1];
                  const netFirst = netWorth.at(0);
                  const fundProfit = profitSeries[0] && profitSeries[0].data.length ? profitSeries[0].data : [];
                  const profitLast = fundProfit[fundProfit.length - 1];
                  document.getElementById('netWorthMetric').textContent = netLast
                    ? '最新 ' + netLast.y.toFixed(4) + ' · 区间 ' + formatChange(netLast.y - netFirst.y, 4)
                    : '--';
                  if (profitLast) {
                    const compareText = profitSeries.slice(1).map((series) => {
                      const last = series.data[series.data.length - 1];
                      return last ? series.name + ' ' + formatChange(last.y, 2) + '%' : '';
                    }).filter(Boolean).join(' · ');
                    document.getElementById('profitMetric').textContent = '本基金 ' + formatChange(profitLast.y, 2) + '%' + (compareText ? ' · ' + compareText : '');
                  } else {
                    document.getElementById('profitMetric').textContent = '--';
                  }
                  document.getElementById('updatedAt').textContent = netLast
                    ? '最新净值日期 ' + formatDate(netLast.x)
                    : '暂无更新时间';
                }

                function formatChange(value, decimals) {
                  const sign = value > 0 ? '+' : '';
                  return sign + value.toFixed(decimals);
                }

                function drawChart(canvas, seriesList, options) {
                  const dpr = window.devicePixelRatio || 1;
                  const rect = canvas.getBoundingClientRect();
                  canvas.width = Math.max(1, Math.floor(rect.width * dpr));
                  canvas.height = Math.max(1, Math.floor(rect.height * dpr));
                  const ctx = canvas.getContext('2d');
                  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
                  ctx.clearRect(0, 0, rect.width, rect.height);
                  const padding = { top: 18, right: 58, bottom: 32, left: 54 };
                  const plotWidth = rect.width - padding.left - padding.right;
                  const plotHeight = rect.height - padding.top - padding.bottom;
                  const points = seriesList.flatMap((series) => series.data);

                  ctx.font = '12px -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif';
                  if (!points.length) {
                    ctx.fillStyle = '#687080';
                    ctx.textAlign = 'center';
                    ctx.fillText('暂无数据', rect.width / 2, rect.height / 2);
                    return;
                  }

                  const xs = points.map((point) => point.x);
                  const ys = points.map((point) => point.y);
                  const minX = Math.min(...xs);
                  const maxX = Math.max(...xs);
                  let minY = Math.min(...ys);
                  let maxY = Math.max(...ys);
                  if (options.zeroLine) {
                    minY = Math.min(minY, 0);
                    maxY = Math.max(maxY, 0);
                  }
                  if (minY === maxY) {
                    minY -= 1;
                    maxY += 1;
                  }
                  const paddingY = (maxY - minY) * 0.08;
                  minY -= paddingY;
                  maxY += paddingY;

                  const xScale = (value) => padding.left + ((value - minX) / Math.max(1, maxX - minX)) * plotWidth;
                  const yScale = (value) => padding.top + (1 - (value - minY) / (maxY - minY)) * plotHeight;

                  drawGrid(ctx, rect, padding, minY, maxY, minX, maxX, options);

                  if (options.zeroLine && minY < 0 && maxY > 0) {
                    const zeroY = yScale(0);
                    ctx.strokeStyle = '#b8beca';
                    ctx.lineWidth = 1;
                    ctx.beginPath();
                    ctx.moveTo(padding.left, zeroY);
                    ctx.lineTo(rect.width - padding.right, zeroY);
                    ctx.stroke();
                  }

                  seriesList.forEach((series, seriesIndex) => {
                    if (!series.data.length) {
                      return;
                    }
                    if (options.fillFirst && seriesIndex === 0) {
                      const gradient = ctx.createLinearGradient(0, padding.top, 0, rect.height - padding.bottom);
                      gradient.addColorStop(0, colorWithAlpha(series.color, 0.22));
                      gradient.addColorStop(1, colorWithAlpha(series.color, 0.02));

                      ctx.beginPath();
                      series.data.forEach((point, index) => {
                        const x = xScale(point.x);
                        const y = yScale(point.y);
                        if (index === 0) {
                          ctx.moveTo(x, y);
                        } else {
                          ctx.lineTo(x, y);
                        }
                      });
                      ctx.lineTo(xScale(series.data[series.data.length - 1].x), rect.height - padding.bottom);
                      ctx.lineTo(xScale(series.data[0].x), rect.height - padding.bottom);
                      ctx.closePath();
                      ctx.fillStyle = gradient;
                      ctx.fill();
                    }

                    ctx.beginPath();
                    series.data.forEach((point, index) => {
                      const x = xScale(point.x);
                      const y = yScale(point.y);
                      if (index === 0) {
                        ctx.moveTo(x, y);
                      } else {
                        ctx.lineTo(x, y);
                      }
                    });
                    ctx.strokeStyle = series.color;
                    ctx.lineWidth = seriesIndex === 0 ? 2 : 1.7;
                    ctx.stroke();
                  });

                  const chartState = {
                    rect: rect,
                    padding: padding,
                    minX: minX,
                    maxX: maxX,
                    xScale: xScale,
                    yScale: yScale,
                    seriesList: seriesList,
                    options: options
                  };
                  state[options.hoverKey + 'State'] = chartState;
                  const hover = state.hover[options.hoverKey];
                  if (hover) {
                    drawHover(ctx, chartState, hover.x);
                  }
                }

                function drawHover(ctx, chart, mouseX) {
                  const plotLeft = chart.padding.left;
                  const plotRight = chart.rect.width - chart.padding.right;
                  const clampedX = Math.max(plotLeft, Math.min(plotRight, mouseX));
                  const xValue = chart.minX + (clampedX - plotLeft) / Math.max(1, plotRight - plotLeft) * (chart.maxX - chart.minX);
                  const nearest = chart.seriesList.map((series) => {
                    if (!series.data.length) {
                      return null;
                    }
                    const point = nearestPoint(series.data, xValue);
                    return { series: series, point: point };
                  }).filter(Boolean);
                  if (!nearest.length) {
                    return;
                  }
                  const anchor = nearest[0].point;
                  const x = chart.xScale(anchor.x);
                  ctx.save();
                  ctx.strokeStyle = 'rgba(32, 36, 44, 0.34)';
                  ctx.lineWidth = 1;
                  ctx.setLineDash([4, 4]);
                  ctx.beginPath();
                  ctx.moveTo(x, chart.padding.top);
                  ctx.lineTo(x, chart.rect.height - chart.padding.bottom);
                  ctx.stroke();
                  ctx.setLineDash([]);

                  nearest.forEach((item) => {
                    const px = chart.xScale(item.point.x);
                    const py = chart.yScale(item.point.y);
                    ctx.fillStyle = '#fff';
                    ctx.strokeStyle = item.series.color;
                    ctx.lineWidth = 2;
                    ctx.beginPath();
                    ctx.arc(px, py, 4, 0, Math.PI * 2);
                    ctx.fill();
                    ctx.stroke();
                  });

                  const lines = [formatFullDate(anchor.x)].concat(nearest.map((item) => {
                    return item.series.name + ' ' + item.point.y.toFixed(chart.options.decimals) + chart.options.suffix;
                  }));
                  drawTooltip(ctx, chart.rect, x, chart.padding.top + 8, lines);
                  ctx.restore();
                }

                function nearestPoint(series, xValue) {
                  let best = series[0];
                  let bestDistance = Math.abs(best.x - xValue);
                  for (let i = 1; i < series.length; i++) {
                    const distance = Math.abs(series[i].x - xValue);
                    if (distance < bestDistance) {
                      best = series[i];
                      bestDistance = distance;
                    }
                  }
                  return best;
                }

                function drawTooltip(ctx, rect, anchorX, anchorY, lines) {
                  ctx.font = '12px -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif';
                  const width = Math.max(...lines.map((line) => ctx.measureText(line).width)) + 20;
                  const height = lines.length * 20 + 12;
                  let x = anchorX + 12;
                  if (x + width > rect.width - 8) {
                    x = anchorX - width - 12;
                  }
                  const y = Math.max(8, Math.min(rect.height - height - 8, anchorY));
                  ctx.fillStyle = 'rgba(255, 255, 255, 0.96)';
                  ctx.strokeStyle = '#cfd6e2';
                  ctx.lineWidth = 1;
                  roundRect(ctx, x, y, width, height, 5);
                  ctx.fill();
                  ctx.stroke();
                  lines.forEach((line, index) => {
                    ctx.fillStyle = index === 0 ? '#20242c' : '#687080';
                    ctx.fillText(line, x + 10, y + 20 + index * 20);
                  });
                }

                function drawNavigator(canvas, series) {
                  const dpr = window.devicePixelRatio || 1;
                  const rect = canvas.getBoundingClientRect();
                  canvas.width = Math.max(1, Math.floor(rect.width * dpr));
                  canvas.height = Math.max(1, Math.floor(rect.height * dpr));
                  const ctx = canvas.getContext('2d');
                  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
                  ctx.clearRect(0, 0, rect.width, rect.height);
                  if (!series.length) {
                    return;
                  }
                  const padding = { left: 54, right: 58, top: 8, bottom: 12 };
                  const plotLeft = padding.left;
                  const plotRight = rect.width - padding.right;
                  const plotTop = padding.top;
                  const plotBottom = rect.height - padding.bottom;
                  const minX = series[0].x;
                  const maxX = series[series.length - 1].x;
                  const ys = series.map((point) => point.y);
                  const minY = Math.min(...ys);
                  const maxY = Math.max(...ys);
                  const xScale = (value) => plotLeft + ((value - minX) / Math.max(1, maxX - minX)) * (plotRight - plotLeft);
                  const yScale = (value) => plotTop + (1 - (value - minY) / Math.max(1, maxY - minY)) * (plotBottom - plotTop);

                  ctx.strokeStyle = '#dfe5ee';
                  ctx.strokeRect(plotLeft, plotTop, plotRight - plotLeft, plotBottom - plotTop);
                  ctx.beginPath();
                  series.forEach((point, index) => {
                    const x = xScale(point.x);
                    const y = yScale(point.y);
                    if (index === 0) {
                      ctx.moveTo(x, y);
                    } else {
                      ctx.lineTo(x, y);
                    }
                  });
                  ctx.strokeStyle = '#9eb7e8';
                  ctx.lineWidth = 1.2;
                  ctx.stroke();

                  const startX = plotLeft + (plotRight - plotLeft) * state.navigator.start;
                  const endX = plotLeft + (plotRight - plotLeft) * state.navigator.end;
                  ctx.fillStyle = 'rgba(47, 111, 221, 0.10)';
                  ctx.fillRect(startX, plotTop, endX - startX, plotBottom - plotTop);
                  ctx.fillStyle = 'rgba(255, 255, 255, 0.64)';
                  ctx.fillRect(plotLeft, plotTop, startX - plotLeft, plotBottom - plotTop);
                  ctx.fillRect(endX, plotTop, plotRight - endX, plotBottom - plotTop);
                  drawHandle(ctx, startX, plotTop, plotBottom);
                  drawHandle(ctx, endX, plotTop, plotBottom);
                  state.navigator.plot = { left: plotLeft, right: plotRight };
                }

                function drawHandle(ctx, x, top, bottom) {
                  ctx.fillStyle = '#ffffff';
                  ctx.strokeStyle = '#8aa7dd';
                  roundRect(ctx, x - 5, top - 2, 10, bottom - top + 4, 4);
                  ctx.fill();
                  ctx.stroke();
                  ctx.strokeStyle = '#8aa7dd';
                  ctx.beginPath();
                  ctx.moveTo(x - 1.5, top + 10);
                  ctx.lineTo(x - 1.5, bottom - 10);
                  ctx.moveTo(x + 1.5, top + 10);
                  ctx.lineTo(x + 1.5, bottom - 10);
                  ctx.stroke();
                }

                function installHover(canvas, key) {
                  canvas.addEventListener('mousemove', (event) => {
                    const rect = canvas.getBoundingClientRect();
                    state.hover[key] = { x: event.clientX - rect.left, y: event.clientY - rect.top };
                    render();
                  });
                  canvas.addEventListener('mouseleave', () => {
                    delete state.hover[key];
                    render();
                  });
                }

                function installNavigator(canvas) {
                  canvas.addEventListener('mousedown', (event) => {
                    const rect = canvas.getBoundingClientRect();
                    const x = event.clientX - rect.left;
                    const plot = state.navigator.plot;
                    if (!plot) {
                      return;
                    }
                    const startX = plot.left + (plot.right - plot.left) * state.navigator.start;
                    const endX = plot.left + (plot.right - plot.left) * state.navigator.end;
                    state.navigator.dragging = Math.abs(x - startX) < Math.abs(x - endX) ? 'start' : 'end';
                    updateNavigatorFromPointer(x);
                  });
                  window.addEventListener('mousemove', (event) => {
                    if (!state.navigator.dragging) {
                      return;
                    }
                    const rect = canvas.getBoundingClientRect();
                    updateNavigatorFromPointer(event.clientX - rect.left);
                  });
                  window.addEventListener('mouseup', () => {
                    state.navigator.dragging = null;
                  });
                }

                function updateNavigatorFromPointer(x) {
                  const plot = state.navigator.plot;
                  if (!plot) {
                    return;
                  }
                  const value = Math.max(0, Math.min(1, (x - plot.left) / Math.max(1, plot.right - plot.left)));
                  const minGap = 0.02;
                  if (state.navigator.dragging === 'start') {
                    state.navigator.start = Math.min(value, state.navigator.end - minGap);
                  } else {
                    state.navigator.end = Math.max(value, state.navigator.start + minGap);
                  }
                  state.rangeDays = 0;
                  document.querySelectorAll('button[data-range]').forEach((item) => item.classList.remove('active'));
                  render();
                }

                function roundRect(ctx, x, y, width, height, radius) {
                  ctx.beginPath();
                  ctx.moveTo(x + radius, y);
                  ctx.lineTo(x + width - radius, y);
                  ctx.quadraticCurveTo(x + width, y, x + width, y + radius);
                  ctx.lineTo(x + width, y + height - radius);
                  ctx.quadraticCurveTo(x + width, y + height, x + width - radius, y + height);
                  ctx.lineTo(x + radius, y + height);
                  ctx.quadraticCurveTo(x, y + height, x, y + height - radius);
                  ctx.lineTo(x, y + radius);
                  ctx.quadraticCurveTo(x, y, x + radius, y);
                  ctx.closePath();
                }

                function drawGrid(ctx, rect, padding, minY, maxY, minX, maxX, options) {
                  const plotLeft = padding.left;
                  const plotRight = rect.width - padding.right;
                  const plotTop = padding.top;
                  const plotBottom = rect.height - padding.bottom;
                  ctx.strokeStyle = '#e5e9f0';
                  ctx.fillStyle = '#687080';
                  ctx.lineWidth = 1;
                  ctx.textAlign = 'right';
                  ctx.textBaseline = 'middle';
                  for (let i = 0; i <= 4; i++) {
                    const y = plotTop + (plotBottom - plotTop) * i / 4;
                    const value = maxY - (maxY - minY) * i / 4;
                    ctx.beginPath();
                    ctx.moveTo(plotLeft, y);
                    ctx.lineTo(plotRight, y);
                    ctx.stroke();
                    ctx.fillText(value.toFixed(options.decimals) + options.suffix, plotLeft - 8, y);
                  }
                  ctx.textAlign = 'center';
                  ctx.textBaseline = 'top';
                  for (let i = 0; i <= 3; i++) {
                    const x = plotLeft + (plotRight - plotLeft) * i / 3;
                    const value = minX + (maxX - minX) * i / 3;
                    ctx.fillText(formatDate(value), x, plotBottom + 10);
                  }
                }

                function colorWithAlpha(hex, alpha) {
                  const bigint = parseInt(hex.replace('#', ''), 16);
                  const r = (bigint >> 16) & 255;
                  const g = (bigint >> 8) & 255;
                  const b = bigint & 255;
                  return `rgba(${'$'}{r}, ${'$'}{g}, ${'$'}{b}, ${'$'}{alpha})`;
                }

                function formatDate(timestamp) {
                  const date = new Date(timestamp);
                  const month = String(date.getMonth() + 1).padStart(2, '0');
                  const day = String(date.getDate()).padStart(2, '0');
                  return `${'$'}{month}-${'$'}{day}`;
                }

                function formatFullDate(timestamp) {
                  const date = new Date(timestamp);
                  const year = date.getFullYear();
                  const month = String(date.getMonth() + 1).padStart(2, '0');
                  const day = String(date.getDate()).padStart(2, '0');
                  return year + '-' + month + '-' + day;
                }

                window.addEventListener('resize', render);
                render();
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun escapeHtml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
