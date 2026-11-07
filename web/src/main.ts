import { mount } from 'svelte'
import './app.css'
import App from './App.svelte'
import { lang } from './lib/i18n'

document.documentElement.lang = lang
mount(App, { target: document.getElementById('app')! })
