import torch
import torch.nn as nn
import torch.optim as optim
import json
import os
import time
import random
import numpy as np
from collections import deque

# --- CONFIGURATION ---
STATE_DIM = 3  # [CPU, RAM, Task_Size]
ACTION_DIM = 5 # Matches number of VMs
LEARNING_RATE = 0.001
GAMMA = 0.95      # Discount factor
EPSILON = 0.2     # 20% exploration, 80% exploitation
MEMORY_SIZE = 2000
BATCH_SIZE = 32

STATE_FILE = "state.json"
ACTION_FILE = "action.json"
REWARD_FILE = "reward.json"

# --- 1. DEEP Q-NETWORK ---
class DQN(nn.Module):
    def __init__(self, input_dim, output_dim):
        super(DQN, self).__init__()
        self.fc = nn.Sequential(
            nn.Linear(input_dim, 128),
            nn.ReLU(),
            nn.Linear(128, 128),
            nn.ReLU(),
            nn.Linear(128, output_dim)
        )

    def forward(self, x):
        return self.fc(x)

# --- 2. AGENT INITIALIZATION ---
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
policy_net = DQN(STATE_DIM, ACTION_DIM).to(device)
optimizer = optim.Adam(policy_net.parameters(), lr=LEARNING_RATE)
memory = deque(maxlen=MEMORY_SIZE)
criterion = nn.MSELoss()

def train_step():
    """Updates the Neural Network using a batch of past experiences."""
    if len(memory) < BATCH_SIZE:
        return

    batch = random.sample(memory, BATCH_SIZE)
    states, actions, rewards, next_states = zip(*batch)

    states = torch.FloatTensor(np.array(states)).to(device)
    actions = torch.LongTensor(actions).unsqueeze(1).to(device)
    rewards = torch.FloatTensor(rewards).to(device)
    next_states = torch.FloatTensor(np.array(next_states)).to(device)

    # Current Q-values predicted by the network
    current_q = policy_net(states).gather(1, actions)

    # Max future Q-value (Bellman Equation logic)
    max_next_q = policy_net(next_states).detach().max(1)[0]
    expected_q = rewards + (GAMMA * max_next_q)

    loss = criterion(current_q.squeeze(), expected_q)
    optimizer.zero_grad()
    loss.backward()
    optimizer.step()

# --- 3. COMMUNICATION & LEARNING LOOP ---
def main_loop():
    last_state = None
    last_action = None

    print(f"Agent Active on {device}. Learning enabled.")

    while True:
        # A. ACTION PHASE: Java asks for a decision
        if os.path.exists(STATE_FILE):
            try:
                with open(STATE_FILE, 'r') as f:
                    data = json.load(f)

                state_vector = [data.get("cpu_load", 0), data.get("ram_load", 0), data.get("task_size", 0)]
                state_tensor = torch.FloatTensor(state_vector).unsqueeze(0).to(device)

                # Epsilon-Greedy Selection
                if random.random() < EPSILON:
                    action = random.randint(0, ACTION_DIM - 1)
                else:
                    with torch.no_grad():
                        action = policy_net(state_tensor).argmax().item()

                # Save context for learning later
                last_state = state_vector
                last_action = action

                with open(ACTION_FILE, 'w') as f:
                    json.dump({"vm_id": action}, f)

                os.remove(STATE_FILE)
            except:
                pass

        # B. LEARNING PHASE: Java sends back results (Rewards)
        if os.path.exists(REWARD_FILE):
            try:
                with open(REWARD_FILE, 'r') as f:
                    reward_data = json.load(f)

                reward = reward_data.get("reward", 0)
                # We assume a static next state for this simplified version
                next_state = [0.5, 0.5, 0]

                if last_state is not None:
                    memory.append((last_state, last_action, reward, next_state))
                    train_step()
                    print(f"Update: Experience added to Replay Buffer. Reward received: {reward:.2f}")

                os.remove(REWARD_FILE)
            except:
                pass

        time.sleep(0.01)

if __name__ == "__main__":
    main_loop()