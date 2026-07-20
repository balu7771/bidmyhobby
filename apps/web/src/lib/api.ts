import axios from 'axios';

// The Backend API Gateway URL (this is typically set via environment variables)
const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

// Create an Axios instance with default configurations
const apiClient = axios.create({
    baseURL: API_BASE_URL,
    withCredentials: true,
    headers: {
        'Content-Type': 'application/json',
    },
});


// --- AI Adapter Service API Contracts ---

export const analyzeHobbyImage = async (imageFile: File) => {
    const formData = new FormData();
    formData.append('image', imageFile);

    // Calls the new AI adapter service to auto-generate the listing
    const response = await apiClient.post('/ai/listing/generate', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data; // Expected: { title, description, category, suggestedBid, tags }
};

export const searchKnowledgeBase = async (query: string) => {
    const response = await apiClient.post('/ai/search', { query });
    return response.data; // Expected: [ { id, content, score, type } ]
};

// --- Authentication Service API Contracts ---

export const sendOTP = async (email: string) => {
    const response = await apiClient.post('/auth/otp/send', { email });
    return response.data;
};

export const verifyOTP = async (email: string, otp: string) => {
    const response = await apiClient.post('/auth/otp/verify', { email, otp });
    return response.data;
};

export default apiClient;
