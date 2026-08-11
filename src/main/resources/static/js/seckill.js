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
                ? `/seckill/${path}/execute`
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
     * 获取当前用户ID（从localStorage获取）
     */
    getCurrentUserId() {
        return localStorage.getItem('seckill_userId');
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
// 9. 登录弹窗
// ========================================
const SeckillLogin = {
    show() {
        const modal = document.getElementById('loginModal');
        if (modal) {
            modal.classList.add('show');
            document.getElementById('loginError').classList.remove('show');
            document.getElementById('loginUsername').value = '';
            document.getElementById('loginPassword').value = '';
            document.getElementById('loginUsername').focus();
        }
    },

    hide() {
        const modal = document.getElementById('loginModal');
        if (modal) {
            modal.classList.remove('show');
        }
    },

    /**
     * 提交登录
     */
    async submit() {
        const username = document.getElementById('loginUsername').value.trim();
        const password = document.getElementById('loginPassword').value;
        const errorEl = document.getElementById('loginError');
        const btn = document.getElementById('loginSubmitBtn');

        // 表单校验
        if (!username) {
            errorEl.textContent = '请输入用户名';
            errorEl.classList.add('show');
            return;
        }
        if (!password) {
            errorEl.textContent = '请输入密码';
            errorEl.classList.add('show');
            return;
        }

        // 禁用按钮，防止重复提交
        btn.disabled = true;
        btn.textContent = '登录中...';
        errorEl.classList.remove('show');

        try {
            const response = await fetch('/user/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username: username, password: password })
            });
            const data = await response.json();

            if (data.code === 200 && data.data && data.data.token) {
                // 登录成功，存储 token 和用户信息
                const token = data.data.token;
                localStorage.setItem('seckill_token', token);
                localStorage.setItem('seckill_userId', data.data.userId);
                localStorage.setItem('seckill_username', data.data.username);
                // 设置 cookie，便于页面加载时识别
                document.cookie = 'Authorization=' + encodeURIComponent('Bearer ' + token) + '; path=/; max-age=86400';

                SeckillLogin.hide();
                SeckillToast.success('登录成功');
                // 刷新页面，显示登录状态
                setTimeout(() => { location.reload(); }, 500);
            } else {
                errorEl.textContent = data.msg || '登录失败，请检查账号密码';
                errorEl.classList.add('show');
            }
        } catch (error) {
            errorEl.textContent = '网络异常，请稍后重试';
            errorEl.classList.add('show');
            console.error('登录请求异常:', error);
        } finally {
            btn.disabled = false;
            btn.textContent = '登录';
        }
    },

    /**
     * 登出
     */
    async logout() {
        try {
            await fetch('/user/logout', { method: 'POST' });
        } catch (e) {
            // 忽略错误
        }
        localStorage.removeItem('seckill_token');
        localStorage.removeItem('seckill_userId');
        localStorage.removeItem('seckill_username');
        localStorage.removeItem('seckill_role');
        document.cookie = 'Authorization=; path=/; max-age=0';
        location.reload();// 刷新页面，显示登录状态
    },

    /**
     * 获取存储的 token
     */
    getToken() {
        return localStorage.getItem('seckill_token');
    },

    /**
     * 初始化：检查登录状态，设置请求拦截
     */
    init() {
        // 确保 modal 元素存在
        const modal = document.getElementById('loginModal');
        if (!modal) return;

        // 支持 Enter 键提交
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' && modal.classList.contains('show')) {
                SeckillLogin.submit();
            }
        });

        // 点击弹窗外部关闭
        modal.addEventListener('click', function (e) {
            if (e.target === this) {
                SeckillLogin.hide();
            }
        });
    },

    /**
     * 切换到注册弹窗
     */
    showRegister() {
        SeckillLogin.hide();
        SeckillRegister.show();
    }
};

// ========================================
// 9-2. 注册弹窗
// ========================================
const SeckillRegister = {
    show() {
        const modal = document.getElementById('registerModal');
        if (modal) {
            modal.classList.add('show');
            document.getElementById('registerError').classList.remove('show');
            document.getElementById('regUsername').value = '';
            document.getElementById('regPassword').value = '';
            document.getElementById('regConfirmPassword').value = '';
            document.getElementById('regPhone').value = '';
            document.getElementById('regEmail').value = '';
            document.getElementById('regUsername').focus();
        }
    },

    hide() {
        const modal = document.getElementById('registerModal');
        if (modal) {
            modal.classList.remove('show');
        }
    },

    /**
     * 切换到登录弹窗
     */
    showLogin() {
        SeckillRegister.hide();
        SeckillLogin.show();
    },

    /**
     * 提交注册
     */
    async submit() {
        const username = document.getElementById('regUsername').value.trim();
        const password = document.getElementById('regPassword').value;
        const confirmPassword = document.getElementById('regConfirmPassword').value;
        const phone = document.getElementById('regPhone').value.trim();
        const email = document.getElementById('regEmail').value.trim();
        const errorEl = document.getElementById('registerError');
        const btn = document.getElementById('registerSubmitBtn');

        // 表单校验
        if (!username) {
            errorEl.textContent = '请输入用户名';
            errorEl.classList.add('show');
            return;
        }
        if (username.length < 3 || username.length > 20) {
            errorEl.textContent = '用户名长度为3-20个字符';
            errorEl.classList.add('show');
            return;
        }
        if (!password) {
            errorEl.textContent = '请输入密码';
            errorEl.classList.add('show');
            return;
        }
        if (password.length < 6 || password.length > 20) {
            errorEl.textContent = '密码长度为6-20个字符';
            errorEl.classList.add('show');
            return;
        }
        if (password !== confirmPassword) {
            errorEl.textContent = '两次输入的密码不一致';
            errorEl.classList.add('show');
            return;
        }

        // 禁用按钮，防止重复提交
        btn.disabled = true;
        btn.textContent = '注册中...';
        errorEl.classList.remove('show');

        try {
            const response = await fetch('/user/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    username: username,
                    password: password,
                    confirmPassword: confirmPassword,
                    phone: phone || null,
                    email: email || null
                })
            });
            const data = await response.json();

            if (data.code === 200) {
                SeckillRegister.hide();
                SeckillToast.success('注册成功，请登录');
                // 自动切换到登录弹窗并填入用户名
                setTimeout(() => {
                    SeckillLogin.show();
                    document.getElementById('loginUsername').value = username;
                    document.getElementById('loginPassword').focus();
                }, 500);
            } else {
                errorEl.textContent = data.msg || '注册失败，请稍后重试';
                errorEl.classList.add('show');
            }
        } catch (error) {
            errorEl.textContent = '网络异常，请稍后重试';
            errorEl.classList.add('show');
            console.error('注册请求异常:', error);
        } finally {
            btn.disabled = false;
            btn.textContent = '注册';
        }
    },

    /**
     * 初始化
     */
    init() {
        const modal = document.getElementById('registerModal');
        if (!modal) return;

        // 支持 Enter 键提交
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' && modal.classList.contains('show')) {
                SeckillRegister.submit();
            }
        });

        // 点击弹窗外部关闭
        modal.addEventListener('click', function (e) {
            if (e.target === this) {
                SeckillRegister.hide();
            }
        });
    }
};

// ========================================
// 10. 验证码弹窗 & 秒杀流程
// ========================================
const SeckillCaptcha = {
    _goodsId: null,
    _captchaKey: null,
    _isFlowing: false,

    /**
     * 启动秒杀流程：登录检查 → 验证码 → 动态URL → 执行秒杀
     */
    startFlow(goodsId) {
        if (this._isFlowing) return;
        this._isFlowing = true;
        this._goodsId = goodsId;

        // 1. 检查登录状态
        const token = SeckillLogin.getToken();
        if (!token) {
            SeckillLogin.show();
            this._isFlowing = false;
            return;
        }

        // 2. 显示验证码弹窗
        this.show();
        this._isFlowing = false;
    },

    /**
     * 显示验证码弹窗，获取验证码图片
     */
    async show() {
        const modal = document.getElementById('captchaModal');
        if (!modal) return;

        modal.classList.add('show');
        document.getElementById('captchaError').classList.remove('show');
        document.getElementById('captchaInput').value = '';
        document.getElementById('captchaInput').focus();

        await this.refresh();
    },

    hide() {
        const modal = document.getElementById('captchaModal');
        if (modal) {
            modal.classList.remove('show');
        }
    },

    /**
     * 刷新验证码
     */
    async refresh() {
        const imgEl = document.getElementById('captchaImage');
        const errorEl = document.getElementById('captchaError');
        errorEl.classList.remove('show');

        try {
            imgEl.src = ''; // 清空旧图片
            const response = await fetch('/user/captcha');
            const data = await response.json();

            if (data.code === 200 && data.data) {
                this._captchaKey = data.data.captchaKey;
                imgEl.src = data.data.captchaImage;
            } else {
                errorEl.textContent = '获取验证码失败，请重试';
                errorEl.classList.add('show');
            }
        } catch (e) {
            errorEl.textContent = '网络异常，请稍后重试';
            errorEl.classList.add('show');
            console.error('获取验证码失败:', e);
        }
    },

    /**
     * 提交验证码 → 获取动态URL → 执行秒杀
     */
    async submit() {
        const captchaText = document.getElementById('captchaInput').value.trim();
        const errorEl = document.getElementById('captchaError');
        const btn = document.getElementById('captchaSubmitBtn');

        if (!captchaText) {
            errorEl.textContent = '请输入验证码';
            errorEl.classList.add('show');
            return;
        }

        btn.disabled = true;
        btn.textContent = '验证中...';
        errorEl.classList.remove('show');

        try {
            // 1. 获取动态URL
            const pathResp = await fetch('/seckill/path?goodsId=' + this._goodsId +
                '&captchaKey=' + encodeURIComponent(this._captchaKey) +
                '&captchaText=' + encodeURIComponent(captchaText));
            const pathData = await pathResp.json();
            console.log('动态URL :', pathData);
            if (pathData.code !== 200) {
                errorEl.textContent = pathData.msg || '验证失败，请重试';
                errorEl.classList.add('show');
                // 刷新验证码
                await this.refresh();
                btn.disabled = false;
                btn.textContent = '确认';
                return;
            }

            const dynamicPath = pathData.data; // 动态URL的hash部分
            this.hide();

            // 2. 执行秒杀
            await SeckillDebounce.execute(this._goodsId, dynamicPath);

        } catch (e) {
            errorEl.textContent = '网络异常，请稍后重试';
            errorEl.classList.add('show');
            console.error('验证码提交异常:', e);
        } finally {
            btn.disabled = false;
            btn.textContent = '确认';
        }
    },

    /**
     * 初始化
     */
    init() {
        // 确保 modal 元素存在
        const modal = document.getElementById('captchaModal');
        if (!modal) return;

        // 支持 Enter 键提交
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' && modal.classList.contains('show')) {
                SeckillCaptcha.submit();
            }
        });

        // 点击遮罩关闭
        modal.addEventListener('click', function (e) {
            if (e.target === this) {
                SeckillCaptcha.hide();
            }
        });
    }
};

// ========================================
// 11. 全局请求拦截（自动注入 JWT token）
// ========================================
(function () {
    const originalFetch = window.fetch;

    window.fetch = function (url, options) {
        options = options || {};

        // 只对同域 API 请求注入 token
        if (typeof url === 'string' && (url.startsWith('/') || url.startsWith(window.location.origin))) {
            const token = localStorage.getItem('seckill_token');
            if (token) {
                options.headers = options.headers || {};
                if (options.headers instanceof Headers) {
                    if (!options.headers.has('Authorization')) {
                        options.headers.set('Authorization', 'Bearer ' + token);
                    }
                } else {
                    // 普通对象
                    if (!options.headers['Authorization']) {
                        options.headers['Authorization'] = 'Bearer ' + token;
                    }
                }
            }
        }

        return originalFetch.call(this, url, options);
    };
})();

// ========================================
// 11. 头部用户信息渲染
// ========================================
const SeckillHeader = {
    /**
     * 渲染头部用户信息
     */
    render() {
        const container = document.getElementById('headerUserInfo');
        if (!container) return;

        const userId = localStorage.getItem('seckill_userId');
        const username = localStorage.getItem('seckill_username');

        if (userId) {
            // 已登录
            container.innerHTML = `
                <span style="font-size: 14px;">欢迎您，<span class="username">${username || '用户'}</span></span>
                <a href="javascript:void(0)" class="logout-btn" onclick="SeckillLogin.logout()" style="margin-left: 8px;">退出登录</a>
                <a href="/" class="logout-btn">← 返回列表</a>
            `;
        } else {
            // 未登录
            container.innerHTML = `
                <a href="javascript:void(0)" class="logout-btn" onclick="SeckillLogin.show()">登录</a>
                <a href="/" class="logout-btn">← 返回列表</a>
            `;
        }
    }
};

// ========================================
// 12. 页面初始化
// ========================================
document.addEventListener('DOMContentLoaded', function () {
    // 初始化登录弹窗
    SeckillLogin.init();

    // 初始化注册弹窗
    SeckillRegister.init();

    // 初始化验证码弹窗
    SeckillCaptcha.init();

    // 初始化图片懒加载
    SeckillLazyLoad.init();

    // 渲染头部用户信息
    SeckillHeader.render();

    // 为商品卡片添加点击跳转
    document.querySelectorAll('.goods-card').forEach(card => {
        card.addEventListener('click', function (e) {
            // 如果点击的是按钮，不跳转
            if (e.target.closest('.seckill-btn')) return;
            const goodsId = this.dataset.goodsId;
            if (goodsId) {
                window.location.href = `/goods/${goodsId}`;
            }
        });
    });
});