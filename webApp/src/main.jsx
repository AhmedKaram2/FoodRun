import React from 'react';
import { createRoot } from 'react-dom/client';
import FoodRunApp from './foodrun/FoodRunApp.jsx';
import './foodrun/foodrun.css';
createRoot(document.getElementById('root')).render(<React.StrictMode><FoodRunApp /></React.StrictMode>);
