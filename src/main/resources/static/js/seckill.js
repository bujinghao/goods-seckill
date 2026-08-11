/**
 * 秒杀系统前端工具函数
 * 包含：防抖、倒计时、图片懒加载、Toast提示、Loading状态等
 */

// ========================================
// 1. 防抖工具（防止重复提交）
// ========================================
const SeckillDebounce = {
    _submitting: false,
    _timer: null,

    /**
     * 执行秒杀请求（带防抖）
     * @param {number} goodsId - 商品ID
     * @param {string} path - 动态URL路径（可选）
     * @returns {Promise}
     */
    async execute(goodsId, path) {
        if (this._submitting) {
            SeckillToast.warn('正在处理中，请勿重复点击');
            return;
        }

        this._submitting = true;
        const btn = document.getElementById('seckillBtn');
        if (btn) {
            btn.disabled = true;
            btn.textContent = '秒杀中...';
        }

        // 显示Loading
        SeckillLoading.show();

        try {
            const url = path
                ? `/seckill/${path}/${goodsId}/execute`
                : `/seckill/${goodsId}/do/async?userId=${SeckillUtils.getCurrentUserId()}`;

            const response = await fetch(url, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            const data = await response.json();

            // 隐藏Loading
            SeckillLoading.hide();

            if (data.code === 200) {
                SeckillToast.success('秒杀请求已提交，正在排队处理...');
                // 显示排队提示
                SeckillQueue.show();
                // 开始轮询订单状态
                SeckillPolling.start(goodsId);
            } else {
                SeckillToast.error(data.msg || '秒杀失败');
            }
        } catch (error) {
            SeckillLoading.hide();
            SeckillToast.error('系统繁忙，请稍后重试');
            console.error('秒杀请求异常:', error);
        } finally {
            // 延迟恢复按钮（防止快速重复点击）
            clearTimeout(this._timer);
            this._timer = setTimeout(() => {
                this._submitting = false;
                if (btn) {
                    btn.disabled = false;
                    btn.textContent = '立即秒杀';
                }
            }, 3000);
        }
    },

    reset() {
        this._submitting = false;
        clearTimeout(this._timer);
        const btn = document.getElementById('seckillBtn');
        if (btn) {
            btn.disabled = false;
            btn.textContent = '立即秒杀';
        }
    }
};

// ========================================
// 2. 倒计时工具
// ========================================
const SeckillCountdown = {
    _timers: {},

    /**
     * 启动倒计时
     * @param {string} elementId - 显示倒计时的元素ID
     * @param {string} startTimeStr - 秒杀开始时间（ISO格式）
     * @param {string} endTimeStr - 秒杀结束时间（ISO格式）
     * @param {function} onStart - 秒杀开始回调
     * @param {function} onEnd - 秒杀结束回调
     */
    start(elementId, startTimeStr, endTimeStr, onStart, onEnd) {
        if (this._timers[elementId]) {
            clearInterval(this._timers[elementId]);
        }

        const startTime = new Date(startTimeStr).getTime();
        const endTime = new Date(endTimeStr).getTime();
        const el = document.getElementById(elementId);

        if (!el) return;

        this._timers[elementId] = setInterval(() => {
            const now = Date.now();

            if (now < startTime) {
                // 距离开始倒计时
                const diff = startTime - now;
                const d = Math.floor(diff / (1000 * 60 * 60 * 24));
                const h = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
                const m = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
                const s = Math.floor((diff % (1000 * 60)) / 1000);

                if (d > 0) {
                    el.innerHTML = `<span class="time-block"><span class="num">${d}</span>天</span>` +
                        `<span class="time-block"><span class="num">${String(h).padStart(2, '0')}</span></span>` +
                        `<span class="separator">:</span>` +
                        `<span class="time-block"><span class="num">${String(m).padStart(2, '0')}</span></span>` +
                        `<span class="separator">:</span>` +
                        `<span class="time-block"><span class="num">${String(s).padStart(2, '0')}</span></span>`;
                } else {
                    el.innerHTML = `<span class="label">距开始：</span>` +
                        `<span class="time-block"><span class="num">${String(h).padStart(2, '0')}</span></span>` +
                        `<span class="separator">:</span>` +
                        `<span class="time-block"><span class="num">${String(m).padStart(2, '0')}</span></span>` +
                        `<span class="separator">:</span>` +
                        `<span class="time-block"><span class="num">${String(s).padStart(2, '0')}</span></span>`;
                }

                const btn = document.getElementById('seckillBtn');
                if (btn) {
                    btn.textContent = '即将开始';
                    btn.disabled = true;
                    btn.className = 'seckill-btn not-started';
                }
            } else if (now >= startTime && now < endTime) {
                // 进行中：显示剩余时间
                const diff = endTime - now;
                const h = Math.floor(diff / (1000 * 60 * 60));
                const m = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
                const s = Math.floor((diff % (1000 * 60)) / 1000);

                el.innerHTML = `<span class="label">剩余：</span>` +
                    `<span class="time-block"><span class="num">${String(h).padStart(2, '0')}</span></span>` +
                    `<span class="separator">:</span>` +
                    `<span class="time-block"><span class="num">${String(m).padStart(2, '0')}</span></span>` +
                    `<span class="separator">:</span>` +
                    `<span class="time-block"><span class="num">${String(s).padStart(2, '0')}</span></span>`;

                const btn = document.getElementById('seckillBtn');
                if (btn) {
                    btn.textContent = '立即秒杀';
                    btn.disabled = false;
                    btn.className = 'seckill-btn';
                }

                if (onStart) onStart();
            } else {
                // 已结束
                el.innerHTML = `<span class="label">秒杀已结束</span>`;
                el.className = 'countdown ended';

                const btn = document.getElementById('seckillBtn');
                if (btn) {
                    btn.textContent = '已结束';
                    btn.disabled = true;
                    btn.className = 'seckill-btn ended';
                }

                clearInterval(this._timers[elementId]);

                if (onEnd) onEnd();
            }
        }, 1000);
    },

    /**
     * 停止倒计时
     * @param {string} elementId
     */
    stop(elementId) {
        if (this._timers[elementId]) {
            clearInterval(this._timers[elementId]);
            delete this._timers[elementId];
        }
    },

    /**
     * 格式化时间显示
     */
    formatTimeDisplay(timeStr) {
        const date = new Date(timeStr);
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        return `${month}-${day} ${hours}:${minutes}`;
    }
};

// ========================================
// 3. Toast 提示
// ========================================
const SeckillToast = {
    show(message, type = 'info', duration = 3000) {
        let toast = document.getElementById('globalToast');
        if (!toast) {
            toast = document.createElement('div');
            toast.id = 'globalToast';
            toast.className = 'toast';
            document.body.appendChild(toast);
        }

        toast.textContent = message;
        toast.className = `toast ${type} show`;

        clearTimeout(this._hideTimer);
        this._hideTimer = setTimeout(() => {
            toast.className = 'toast';
        }, duration);
    },

    success(msg) { this.show(msg, 'success'); },
    error(msg) { this.show(msg, 'error', 5000); },
    info(msg) { this.show(msg, 'info'); },
    warn(msg) { this.show(msg, 'error'); }
};

// ========================================
// 4. Loading 状态
// ========================================
const SeckillLoading = {
    show() {
        let overlay = document.getElementById('loadingOverlay');
        if (!overlay) {
            overlay = document.createElement('div');
            overlay.id = 'loadingOverlay';
            overlay.className = 'loading-overlay';
            overlay.innerHTML = `
                <div class="loading-content">
                    <div class="loading-spinner"></div>
                    <div class="loading-text">处理中...</div>
                </div>
            `;
            document.body.appendChild(overlay);
        }
        overlay.classList.add('show');
    },

    hide() {
        const overlay = document.getElementById('loadingOverlay');
        if (overlay) {
            overlay.classList.remove('show');
        }
    }
};

// ========================================
// 5. 排队提示
// ========================================
const SeckillQueue = {
    show() {
        const modal = document.getElementById('queueModal');
        if (modal) {
            modal.classList.add('show');
        }
    },

    hide() {
        const modal = document.getElementById('queueModal');
        if (modal) {
            modal.classList.remove('show');
        }
    }
};

// ========================================
// 6. 订单轮询
// ========================================
const SeckillPolling = {
    _timer: null,
    _maxRetries: 60, // 最多轮询60次（5分钟）
    _retryCount: 0,

    /**
     * 开始轮询订单状态
     * @param {number} goodsId - 商品ID
     * @param {number} interval - 轮询间隔（毫秒）
     */
    start(goodsId, interval = 5000) {
        this.stop();
        this._retryCount = 0;

        // 显示轮询指示器
        const indicator = document.getElementById('pollingIndicator');
        if (indicator) {
            indicator.classList.add('show');
        }

        this._timer = setInterval(async () => {
            this._retryCount++;

            if (this._retryCount > this._maxRetries) {
                this.stop();
                SeckillToast.info('查询次数已超限，请前往订单中心查看结果');
                SeckillQueue.hide();
                return;
            }

            try {
                const userId = SeckillUtils.getCurrentUserId();
                const response = await fetch(`/seckill/order/check?userId=${userId}&goodsId=${goodsId}`);
                const data = await response.json();

                if (data.code === 200 && data.data) {
                    this.stop();
                    SeckillQueue.hide();

                    const order = data.data;
                    // 跳转到结果页
                    window.location.href = `/order/result?orderNo=${order.orderNo}&goodsId=${goodsId}`;
                }
            } catch (error) {
                console.error('轮询订单状态失败:', error);
            }
        }, interval);
    },

    stop() {
        if (this._timer) {
            clearInterval(this._timer);
            this._timer = null;
        }
        const indicator = document.getElementById('pollingIndicator');
        if (indicator) {
            indicator.classList.remove('show');
        }
    }
};

// ========================================
// 7. 图片懒加载
// ========================================
const SeckillLazyLoad = {
    init() {
        const images = document.querySelectorAll('.lazy-img');
        if ('IntersectionObserver' in window) {
            const observer = new IntersectionObserver((entries) => {
                entries.forEach(entry => {
                    if (entry.isIntersecting) {
                        const img = entry.target;
                        const src = img.dataset.src;
                        if (src) {
                            img.src = src;
                            img.classList.add('loaded');
                        }
                        observer.unobserve(img);
                    }
                });
            }, {
                rootMargin: '50px 0px',
                threshold: 0.01
            });

            images.forEach(img => observer.observe(img));
        } else {
            // 降级处理：直接加载所有图片
            images.forEach(img => {
                const src = img.dataset.src;
                if (src) {
                    img.src = src;
                    img.classList.add('loaded');
                }
            });
        }
    }
};

// ========================================
// 8. 工具函数
// ========================================
const SeckillUtils = {
    /**
     * 获取当前用户ID（从页面meta标签或data属性获取）
     */
    getCurrentUserId() {
        const meta = document.querySelector('meta[name="current-user-id"]');
        return meta ? meta.getAttribute('content') : null;
    },

    /**
     * 格式化金额
     */
    formatPrice(price) {
        return parseFloat(price).toFixed(2);
    },

    /**
     * 获取状态文本
     */
    getStatusText(status) {
        const map = {
            0: '未开始',
            1: '进行中',
            2: '已结束'
        };
        return map[status] || '未知';
    },

    /**
     * 获取订单状态文本
     */
    getOrderStatusText(status) {
        const map = {
            0: '待支付',
            1: '已支付',
            2: '已取消',
            3: '已超时'
        };
        return map[status] || '未知';
    }
};

// ========================================
// 9. 页面初始化
// ========================================
document.addEventListener('DOMContentLoaded', function() {
    // 初始化图片懒加载
    SeckillLazyLoad.init();

    // 为商品卡片添加点击跳转
    document.querySelectorAll('.goods-card').forEach(card => {
        card.addEventListener('click', function(e) {
            // 如果点击的是按钮，不跳转
            if (e.target.closest('.seckill-btn')) return;
            const goodsId = this.dataset.goodsId;
            if (goodsId) {
                window.location.href = `/goods/${goodsId}`;
            }
        });
    });
});