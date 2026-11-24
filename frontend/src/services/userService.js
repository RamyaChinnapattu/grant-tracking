import api from "./api";

const API_URL = "/users";

// Admin: get all registered users (Beneficiaries, Officers, Admins)
export const getUsers = () => api.get(API_URL);

// Admin: get user by ID
export const getUserById = (id) => api.get(`${API_URL}/${id}`);

// Admin: create user (Officer or Admin)
export const createUser = (data) => api.post(API_URL, data);

// Admin: update user details, role, jurisdiction or active status
export const updateUser = (id, data) => api.put(`${API_URL}/${id}`, data);

// Admin: delete user
export const deleteUser = (id) => api.delete(`${API_URL}/${id}`);
