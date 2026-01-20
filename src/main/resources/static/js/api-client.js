/**
 * Bookd API Client
 * 
 * 统一的 API 客户端封装，提供：
 * 1. 自动解包 SuccessResponse { data: ... } 格式
 * 2. 统一错误处理（ErrorResponse { code, message }）
 * 3. 自动添加 Authorization 和 Accept-Language 请求头
 * 4. 处理 204 No Content 响应
 */

// API 基础路径
const API_BASE = '/api';

/**
 * API 错误类
 */
class ApiError extends Error {
    constructor(code, message, status, details = null) {
        super(message);
        this.name = 'ApiError';
        this.code = code;
        this.status = status;
        this.details = details;
    }
}

/**
 * 获取浏览器语言设置
 * @returns {string} 语言代码，如 'zh-CN' 或 'en'
 */
function getBrowserLanguage() {
    const lang = navigator.language || navigator.userLanguage || 'zh-CN';
    // 简化处理：zh-* 映射为 zh-CN，其他映射为 en
    return lang.startsWith('zh') ? 'zh-CN' : 'en';
}

/**
 * 获取认证令牌
 * @returns {string|null}
 */
function getAuthToken() {
    return localStorage.getItem('authToken');
}

/**
 * 统一 API 客户端
 * 
 * @param {string} url - API 路径
 * @param {object} options - fetch 选项
 * @param {boolean} options.skipUnwrap - 跳过响应解包（用于特殊场景）
 * @returns {Promise<any>} 解包后的数据
 * @throws {ApiError} API 错误
 */
async function apiClient(url, options = {}) {
    const { skipUnwrap = false, ...fetchOptions } = options;
    
    // 构建请求头
    const headers = {
        'Accept-Language': getBrowserLanguage(),
        ...fetchOptions.headers
    };
    
    // 如果不是 FormData，添加 Content-Type
    if (!(fetchOptions.body instanceof FormData)) {
        headers['Content-Type'] = 'application/json';
    }
    
    // 添加认证令牌
    const token = getAuthToken();
    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }
    
    try {
        const response = await fetch(url, {
            ...fetchOptions,
            headers
        });
        
        // 处理 204 No Content（删除操作）
        if (response.status === 204) {
            return { success: true };
        }
        
        // 解析 JSON 响应
        const json = await response.json();
        
        // 处理错误响应
        if (!response.ok) {
            // 新格式：{ code, message, details, timestamp, path }
            throw new ApiError(
                json.code || 'UNKNOWN',
                json.message || '操作失败',
                response.status,
                json.details
            );
        }
        
        // 如果跳过解包，直接返回原始响应
        if (skipUnwrap) {
            return json;
        }
        
        // 成功响应：解包 { data: ..., timestamp, path }
        // 直接返回 data 字段内容
        return json.data;
        
    } catch (error) {
        // 网络错误或 JSON 解析错误
        if (error instanceof ApiError) {
            throw error;
        }
        throw new ApiError('NETWORK_ERROR', '网络错误，请稍后重试', 0, error.message);
    }
}

/**
 * 便捷 API 方法
 */
const api = {
    /**
     * GET 请求
     * @param {string} url - API 路径
     * @param {object} options - 额外选项
     * @returns {Promise<any>}
     */
    get: (url, options = {}) => apiClient(url, { method: 'GET', ...options }),
    
    /**
     * POST 请求
     * @param {string} url - API 路径
     * @param {any} data - 请求体数据
     * @param {object} options - 额外选项
     * @returns {Promise<any>}
     */
    post: (url, data, options = {}) => apiClient(url, { 
        method: 'POST', 
        body: data instanceof FormData ? data : JSON.stringify(data),
        ...options
    }),
    
    /**
     * PUT 请求
     * @param {string} url - API 路径
     * @param {any} data - 请求体数据
     * @param {object} options - 额外选项
     * @returns {Promise<any>}
     */
    put: (url, data, options = {}) => apiClient(url, { 
        method: 'PUT', 
        body: JSON.stringify(data),
        ...options
    }),
    
    /**
     * DELETE 请求
     * @param {string} url - API 路径
     * @param {object} options - 额外选项
     * @returns {Promise<any>}
     */
    delete: (url, options = {}) => apiClient(url, { method: 'DELETE', ...options }),
    
    /**
     * 文件上传（multipart/form-data）
     * @param {string} url - API 路径
     * @param {FormData} formData - 表单数据
     * @param {object} options - 额外选项
     * @returns {Promise<any>}
     */
    upload: (url, formData, options = {}) => apiClient(url, {
        method: 'POST',
        body: formData,
        headers: {}, // 让浏览器自动设置 Content-Type
        ...options
    })
};

// 导出到全局（供 HTML 内联脚本使用）
window.ApiError = ApiError;
window.api = api;
window.apiClient = apiClient;
window.API_BASE = API_BASE;
