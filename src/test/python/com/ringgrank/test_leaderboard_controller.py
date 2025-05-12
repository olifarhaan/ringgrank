import pytest
import requests
import time
import random
import asyncio
import aiohttp
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import List, Dict, Any
import os

BASE_URL = os.getenv("BASE_URL", "http://localhost:8080/api/v1")

TARGET_GAME_ID = int(os.getenv("TARGET_GAME_ID", 1))
NUM_USERS = int(os.getenv("NUM_USERS", 1000000))
NUM_SCORE_WRITES = int(os.getenv("NUM_SCORE_WRITES", 10000))
NUM_LEADER_READS = int(os.getenv("NUM_LEADER_READS", 10000))
NUM_RANK_READS = int(os.getenv("NUM_RANK_READS", 10000))
WORKER_THREADS = int(os.getenv("WORKER_THREADS", 10000))

ID_POINTER=100001

def generate_score_payload() -> Dict[str, Any]:
    """Generates a random score submission payload."""
    score = random.randint(1, 100000)
    timestamp = int(time.time() * 1000)
    return {
        "userId": ID_POINTER,
        "gameId": TARGET_GAME_ID,
        "score": score,
        "timestamp": timestamp
    }

def test_score():
    score = generate_score_payload()
    response = requests.post(f"{BASE_URL}/scores", json=score)
    assert response.status_code == 202


def post_score(session: requests.Session, payload: Dict[str, Any]) -> requests.Response:
    """Sends a single score submission request."""
    try:
        response = session.post(f"{BASE_URL}/scores", json=payload)
        return response
    except requests.exceptions.RequestException as e:
        print(f"Error posting score: {e}")
        return None

def get_leaders(session: requests.Session, game_id: int, limit: int, window: str = None) -> requests.Response:
    """Fetches top K leaders."""
    params = {"limit": limit}
    if window:
        params["window"] = window
    try:
        response = session.get(f"{BASE_URL}/games/{game_id}/leaders", params=params, timeout=5)
        return response
    except requests.exceptions.RequestException as e:
        print(f"Error getting leaders: {e}")
        return None

def get_rank(session: requests.Session, game_id: int, user_id: int, window: str = None) -> requests.Response:
    """Fetches a user's rank."""
    params = {}
    if window:
        params["window"] = window
    try:
        response = session.get(f"{BASE_URL}/games/{game_id}/users/{user_id}/rank", params=params, timeout=5)
        return response
    except requests.exceptions.RequestException as e:
        print(f"Error getting rank: {e}")
        return None
    
async def post_score_async(session: aiohttp.ClientSession, payload: Dict[str, Any]) -> Dict:
    start_time = time.time()  # Record start time
    try:
        async with session.post(f"{BASE_URL}/scores", json=payload) as response:
            text = await response.text()  # Ensure response is complete
            return {
                'status': response.status,
                'elapsed': time.time() - start_time,  # Calculate elapsed time
                'text': text
            }
    except Exception as e:
        print(f"Error posting score: {e}")
        return None
    
async def get_leaders_async(session: aiohttp.ClientSession, game_id: int, limit: int, window: str = None) -> requests.Response:
    """Fetches top K leaders."""
    params = {"limit": limit}
    if window:
        params["window"] = window
    try:
        async with session.get(f"{BASE_URL}/games/{game_id}/leaders", params=params, timeout=5) as response:
            return response
    except Exception as e:
        print(f"Error getting leaders: {e}")
        return None

async def get_rank_async(session: aiohttp.ClientSession, game_id: int, user_id: int, window: str = None) -> requests.Response:
    """Fetches a user's rank."""
    params = {}
    if window:
        params["window"] = window
    try:
        async with session.get(f"{BASE_URL}/games/{game_id}/users/{user_id}/rank", params=params, timeout=5) as response:
            return response
    except Exception as e:
        print(f"Error getting rank: {e}")
        return None

@pytest.mark.asyncio
async def test_score_ingestion_performance():
    """Tests score ingestion throughput using aiohttp."""
    print(f"\nStarting score ingestion test: {NUM_SCORE_WRITES} writes")
    
    payloads = [
        {**generate_score_payload(), "userId": i + 1}
        for i in range(NUM_SCORE_WRITES)
    ]
    
    successful_requests = 0
    failed_requests = 0
    latencies = []
    
    start_time = time.time()
    
    connector = aiohttp.TCPConnector(limit=0)  # No limit on concurrent connections
    timeout = aiohttp.ClientTimeout(total=30)
    
    async with aiohttp.ClientSession(connector=connector, timeout=timeout) as session:
        tasks = [post_score_async(session, payload) for payload in payloads]
        responses = await asyncio.gather(*tasks)
        
        for response in responses:
            if response and response['status'] == 202:
                successful_requests += 1
                latencies.append(response['elapsed'])  # Use our calculated elapsed time
            else:
                failed_requests += 1
                if response:
                    print(f"Failed request: Status {response['status']}, Body: {response['text'][:200]}")

    end_time = time.time()
    duration = end_time - start_time
    rps = successful_requests / duration if duration > 0 else 0
    avg_latency = sum(latencies) / len(latencies) if latencies else 0

    print(f"\n--- Score Ingestion Results ---")
    print(f"Total time: {duration:.2f}s")
    print(f"Successful requests: {successful_requests}")
    print(f"Failed requests: {failed_requests}")
    print(f"Requests per second (RPS): {rps:.2f}")
    print(f"Average latency: {int(avg_latency * 1000)}ms")
    assert failed_requests == 0

@pytest.mark.asyncio
async def test_get_top_k_leaders_performance():
    """Tests fetching top-K leaders."""
    print(f"\nStarting top-K leaders read test: {NUM_LEADER_READS} reads, {WORKER_THREADS} threads per worker.")
    
    successful_requests = 0
    failed_requests = 0
    latencies = []
    limit = 1000

    start_time = time.time()

    with aiohttp.ClientSession() as session:
        tasks = [get_leaders_async(session, TARGET_GAME_ID, limit) for _ in range(NUM_LEADER_READS)]
        responses = await asyncio.gather(*tasks)
        for response in responses:
            if response and response['status'] == 200:
                successful_requests += 1
                latencies.append(response['elapsed'])
            else:
                failed_requests += 1
                if response:
                    print(f"Failed request: Status {response['status']}, Body: {response['text'][:200]}")
                else:
                    failed_requests += 1

    end_time = time.time()
    duration = end_time - start_time
    rps = successful_requests / duration if duration > 0 else 0
    avg_latency = sum(latencies) / len(latencies) if latencies else 0

    print(f"\n--- Top-K Leaders Read Results ---")
    print(f"Total time: {duration:.2f}s")
    print(f"Successful requests: {successful_requests}")
    print(f"Failed requests: {failed_requests}")
    print(f"Requests per second (RPS): {rps:.2f}")
    print(f"Average latency: {int(avg_latency * 1000)}ms")


@pytest.mark.asyncio
async def test_get_player_rank_performance():
    print(f"\nStarting player rank read test: {NUM_RANK_READS} reads, {WORKER_THREADS} threads per worker.")

    successful_requests = 0
    failed_requests = 0
    latencies = []
    start_time = time.time()

    with aiohttp.ClientSession() as session:
        tasks = [get_rank_async(session, TARGET_GAME_ID, random.randint(ID_POINTER, ID_POINTER + NUM_USERS)) for _ in range(NUM_RANK_READS)]
        responses = await asyncio.gather(*tasks)
        for response in responses:
            if response and response['status'] == 200:
                successful_requests += 1
                latencies.append(response['elapsed'])
            else:
                failed_requests += 1
                if response:
                    print(f"Failed request: Status {response['status']}, Body: {response['text'][:200]}")
                else:
                    failed_requests += 1

    end_time = time.time()
    duration = end_time - start_time
    rps = successful_requests / duration if duration > 0 else 0
    avg_latency = sum(latencies) / len(latencies) if latencies else 0

    print(f"\n--- Player Rank Read Results ---")
    print(f"Total time: {duration:.2f}s")
    print(f"Successful requests: {successful_requests}")
    print(f"Failed requests: {failed_requests}")
    print(f"Requests per second (RPS): {rps:.2f}")
    print(f"Average latency: {int(avg_latency * 1000)}ms")
