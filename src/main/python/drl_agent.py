import torch
import torch.nn as nn
import torch.optim as optim
import json
import os
import time
import random
import numpy as np
from collections import deque
import csv

# --- CONFIGURATION ---
STATE_DIM = 6 # [Load0, Load1, Load2, Load3, Load4, Task_Size]
ACTION_DIM = 5
LEARNING_RATE = 0.001  # Lowered to prevent exploding gradients
GAMMA = 0.95
EPSILON = 0.5
EPSILON_DECAY = 0.995
EPSILON_MIN = 0.01
MEMORY_SIZE = 10000
BATCH_SIZE = 64

STATE_FILE = "state.json"
ACTION_FILE = "action.json"
REWARD_FILE = "reward.json"
DONE_FILE = "training_done.flag"

MODEL_PATH = "agent_brain.pth"
LOG_FILE = "training_metrics.csv"

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

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
policy_net = DQN(STATE_DIM, ACTION_DIM).to(device)

# --- RESILIENT BRAIN LOADING ---
if os.path.exists(MODEL_PATH):
    try:
        policy_net.load_state_dict(torch.load(MODEL_PATH))
        print(f"Loaded existing brain from {MODEL_PATH}")
    except RuntimeError:
        print("Old brain architecture detected (Shape Mismatch). Starting with a fresh brain!")
        # If the shapes don't match, PyTorch uses the randomly initialized weights of the new DQN

optimizer = optim.Adam(policy_net.parameters(), lr=LEARNING_RATE)
memory = deque(maxlen=MEMORY_SIZE)
criterion = nn.MSELoss()
epoch_counter = 0

if not os.path.exists(LOG_FILE):
    with open(LOG_FILE, 'w', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(["Epoch", "Average_Loss", "Epsilon"])

def train_step():
    if len(memory) < BATCH_SIZE:
        return 0.0

    batch = random.sample(memory, BATCH_SIZE)
    states, actions, rewards, next_states = zip(*batch)

    states = torch.FloatTensor(np.array(states)).to(device)
    actions = torch.LongTensor(actions).unsqueeze(1).to(device)
    rewards = torch.FloatTensor(rewards).to(device)
    next_states = torch.FloatTensor(np.array(next_states)).to(device)

    current_q = policy_net(states).gather(1, actions)
    max_next_q = policy_net(next_states).detach().max(1)[0]
    expected_q = rewards + (GAMMA * max_next_q)

    loss = criterion(current_q.squeeze(), expected_q)
    optimizer.zero_grad()
    loss.backward()
    optimizer.step()

    return loss.item()

def main_loop():
    global epoch_counter, EPSILON
    last_state = None
    last_action = None
    losses = []

    print(f"Agent Active on {device}. Waiting for Java...")

    while True:
        if os.path.exists(DONE_FILE):
            print("\nReceived STOP signal from Java.")
            os.remove(DONE_FILE)
            break

        # A. ACTION PHASE
        if os.path.exists(STATE_FILE):
            try:
                with open(STATE_FILE, 'r') as f:
                    data = json.load(f)

                # Now expecting normalized float data
                state_vector = [
                    data.get("l0", 0.0),
                    data.get("l1", 0.0),
                    data.get("l2", 0.0),
                    data.get("l3", 0.0),
                    data.get("l4", 0.0),
                    data.get("task_size", 0.0)
                ]
                state_tensor = torch.FloatTensor(state_vector).unsqueeze(0).to(device)

                if random.random() < EPSILON:
                    action = random.randint(0, ACTION_DIM - 1)
                else:
                    with torch.no_grad():
                        action = policy_net(state_tensor).argmax().item()

                last_state = state_vector
                last_action = action

                with open(ACTION_FILE, 'w') as f:
                    json.dump({"vm_id": action}, f)
                os.remove(STATE_FILE)
            except:
                pass

        # B. LEARNING PHASE
        if os.path.exists(REWARD_FILE):
            try:
                with open(REWARD_FILE, 'r') as f:
                    reward = json.load(f).get("reward", 0.0)

                next_state = [max(0.0, l - 0.1) for l in last_state[:5]] + [0.0]

                if last_state is not None:
                    memory.append((last_state, last_action, reward, next_state))

                    loss = train_step()
                    if loss > 0: losses.append(loss)

                os.remove(REWARD_FILE)
            except:
                pass

        # Epoch Tracking via Loss array length
        if len(losses) >= 500:
            epoch_counter += 1
            avg_loss = sum(losses) / len(losses)

            EPSILON = max(EPSILON_MIN, EPSILON * EPSILON_DECAY)

            with open(LOG_FILE, 'a', newline='') as f:
                writer = csv.writer(f)
                writer.writerow([epoch_counter, avg_loss, EPSILON])

            torch.save(policy_net.state_dict(), MODEL_PATH)
            print(f"Epoch {epoch_counter} | Avg Loss: {avg_loss:.4f} | Epsilon: {EPSILON:.3f}")
            losses.clear()

        time.sleep(0.002)

if __name__ == "__main__":
    main_loop()
    print("Training session closed safely.")