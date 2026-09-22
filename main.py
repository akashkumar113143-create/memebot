import hmac
import hashlib
import time
import requests
import json
import os

# API Configurations from Render Environment Variables
DELTA_API_KEY = os.getenv("DELTA_API_KEY", "")
DELTA_API_SECRET = os.getenv("DELTA_API_SECRET", "")
TELEGRAM_BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN", "")
TELEGRAM_CHAT_ID = os.getenv("TELEGRAM_CHAT_ID", "")

BASE_URL = "https://api.delta.exchange"
MAX_DAILY_LOSS_USD = 20.0
RISK_PER_TRADE_USD = 1.0  # Chote balance ($5) ke hisab se $1 risk per trade

# Telegram Notification Helper
def send_telegram_alert(message):
    if TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID:
        try:
            url = f"https://api.telegram.org/bot{TELEGRAM_BOT_TOKEN}/sendMessage"
            payload = {"chat_id": TELEGRAM_CHAT_ID, "text": message}
            requests.post(url, json=payload, timeout=5)
        except Exception as e:
            print("Telegram alert error:", e)

print("🤖 MemeBot v3 Active & Scanning Market 24/7...")
send_telegram_alert("🚀 MemeBot v3 Bot Started Successfully on Cloud Server!")

def get_ticker():
    try:
        res = requests.get(f"{BASE_URL}/v2/tickers/BTCUSD")
        return res.json()
    except Exception as e:
        print("Market data error:", e)
        return None

# Main Automated Loop
while True:
    data = get_ticker()
    if data:
        print("Live BTC Market Data Scan Active.")
    time.sleep(60)
