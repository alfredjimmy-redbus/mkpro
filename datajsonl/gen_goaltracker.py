import json
import random
import uuid

# GoalTracker training data generator
# Produces 10000 JSONL lines covering goal creation, decomposition, status updates, queries

SYSTEM_PROMPT = "You are the GoalTracker agent for mkpro. Your role is to manage project goals and TODO items. You create goals, decompose them into sub-goals, track progress, update statuses (PENDING, IN_PROGRESS, COMPLETED, FAILED), and report on project progress. You maintain a hierarchical goal tree. Respond with structured goal operations and clear status reports."

# Goal domains and their sub-tasks
PROJECTS = [
    {
        "name": "E-commerce Platform",
        "goals": [
            "Implement user authentication",
            "Build product catalog",
            "Create shopping cart",
            "Implement payment processing",
            "Build order management system",
            "Add search and filtering",
            "Implement user reviews",
            "Create admin dashboard",
            "Set up email notifications",
            "Implement inventory management",
        ],
        "sub_tasks": {
            "Implement user authentication": ["Set up JWT token generation", "Create login endpoint", "Create registration endpoint", "Add password hashing", "Implement refresh tokens", "Add email verification"],
            "Build product catalog": ["Design product schema", "Create CRUD endpoints", "Add image upload", "Implement categories", "Add product variants"],
            "Create shopping cart": ["Design cart data model", "Add item to cart", "Remove item from cart", "Calculate totals", "Handle stock validation"],
            "Implement payment processing": ["Integrate Stripe SDK", "Create payment intent endpoint", "Handle webhooks", "Add refund functionality", "Implement receipt generation"],
            "Build order management system": ["Create order model", "Implement order placement", "Add order status tracking", "Create order history", "Send order confirmations"],
        }
    },
    {
        "name": "Task Management App",
        "goals": [
            "Set up project structure",
            "Implement task CRUD",
            "Add user management",
            "Create team collaboration features",
            "Build notification system",
            "Implement file attachments",
            "Add real-time updates",
            "Create reporting dashboard",
            "Add calendar integration",
            "Implement API rate limiting",
        ],
        "sub_tasks": {
            "Set up project structure": ["Initialize project with framework", "Configure database", "Set up testing framework", "Add linting and formatting", "Configure CI pipeline"],
            "Implement task CRUD": ["Design task model", "Create task endpoints", "Add task validation", "Implement task assignment", "Add due date handling"],
            "Add user management": ["Create user model", "Implement registration", "Add role-based access", "Create user profiles", "Add avatar upload"],
            "Create team collaboration features": ["Implement team creation", "Add member invitations", "Create shared workspaces", "Add comments on tasks", "Implement @mentions"],
            "Build notification system": ["Design notification schema", "Create in-app notifications", "Add email notifications", "Implement push notifications", "Add notification preferences"],
        }
    },
    {
        "name": "Chat Application",
        "goals": [
            "Set up WebSocket infrastructure",
            "Implement direct messaging",
            "Create group chat",
            "Add message persistence",
            "Implement file sharing",
            "Add typing indicators",
            "Create message search",
            "Implement end-to-end encryption",
            "Add message reactions",
            "Build presence system",
        ],
        "sub_tasks": {
            "Set up WebSocket infrastructure": ["Configure WebSocket server", "Implement connection handling", "Add heartbeat mechanism", "Handle reconnection", "Add connection pooling"],
            "Implement direct messaging": ["Create message model", "Build send/receive flow", "Add read receipts", "Implement message history", "Add message deletion"],
            "Create group chat": ["Design group model", "Implement group creation", "Add member management", "Handle group permissions", "Add group metadata"],
        }
    },
    {
        "name": "API Microservices",
        "goals": [
            "Design service boundaries",
            "Implement user service",
            "Create order service",
            "Build payment service",
            "Set up API gateway",
            "Implement service discovery",
            "Add distributed tracing",
            "Create event bus",
            "Implement circuit breakers",
            "Add health checks",
        ],
        "sub_tasks": {
            "Design service boundaries": ["Identify bounded contexts", "Define service contracts", "Design event schemas", "Plan data ownership", "Document dependencies"],
            "Implement user service": ["Create user endpoints", "Add authentication", "Implement user profiles", "Add role management", "Create user search"],
            "Set up API gateway": ["Configure routing rules", "Add rate limiting", "Implement auth forwarding", "Add request transformation", "Set up load balancing"],
        }
    },
    {
        "name": "Mobile App (Android)",
        "goals": [
            "Set up project architecture",
            "Implement home screen",
            "Create user profile",
            "Add push notifications",
            "Implement offline mode",
            "Create onboarding flow",
            "Add biometric login",
            "Implement deep linking",
            "Create widget",
            "Add analytics tracking",
        ],
        "sub_tasks": {
            "Set up project architecture": ["Configure MVVM with Hilt", "Set up Room database", "Configure Retrofit", "Add navigation component", "Set up build variants"],
            "Implement home screen": ["Design home layout", "Create RecyclerView adapter", "Add pull-to-refresh", "Implement pagination", "Add shimmer loading"],
        }
    },
]

STATUSES = ["PENDING", "IN_PROGRESS", "COMPLETED", "FAILED"]

def generate_create_goal():
    project = random.choice(PROJECTS)
    goal = random.choice(project["goals"])
    user_msgs = [
        f"Create a goal: {goal}",
        f"Add a new goal for: {goal}",
        f"I want to track: {goal}",
        f"New task: {goal}",
        f"Please add this to our goals: {goal}",
        f"We need to {goal.lower()}",
        f"Add '{goal}' to the project goals",
        f"Track this: {goal}",
    ]
    
    goal_id = str(uuid.uuid4())[:8]
    response = f"Goal created successfully.\n\n**Goal:** {goal}\n**ID:** {goal_id}\n**Status:** PENDING\n**Project:** {project['name']}\n\nI've added this to the goal tree. Would you like me to break it down into sub-tasks?"
    
    return {"messages": [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": random.choice(user_msgs)},
        {"role": "assistant", "content": response}
    ]}

def generate_decompose_goal():
    project = random.choice(PROJECTS)
    goals_with_subtasks = [g for g in project["goals"] if g in project.get("sub_tasks", {})]
    if not goals_with_subtasks:
        return generate_create_goal()
    
    goal = random.choice(goals_with_subtasks)
    subtasks = project["sub_tasks"][goal]
    
    user_msgs = [
        f"Break down '{goal}' into subtasks",
        f"Decompose the goal: {goal}",
        f"What are the steps for '{goal}'?",
        f"Create subtasks for: {goal}",
        f"I need a detailed plan for: {goal}",
        f"Split '{goal}' into actionable items",
    ]
    
    response_parts = [f"I've decomposed **{goal}** into {len(subtasks)} sub-tasks:\n"]
    for i, st in enumerate(subtasks, 1):
        sid = str(uuid.uuid4())[:8]
        response_parts.append(f"{i}. [{sid}] {st} — PENDING")
    response_parts.append(f"\nAll sub-tasks are set to PENDING. The parent goal status will automatically update based on sub-task progress.")
    
    return {"messages": [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": random.choice(user_msgs)},
        {"role": "assistant", "content": "\n".join(response_parts)}
    ]}

def generate_status_update():
    project = random.choice(PROJECTS)
    goal = random.choice(project["goals"])
    new_status = random.choice(["IN_PROGRESS", "COMPLETED", "FAILED"])
    
    user_msgs = [
        f"Mark '{goal}' as {new_status.lower().replace('_', ' ')}",
        f"Update status of '{goal}' to {new_status}",
        f"'{goal}' is now {new_status.lower().replace('_', ' ')}",
        f"Change '{goal}' status to {new_status}",
        f"Set {goal} = {new_status}",
    ]
    
    status_emoji = {"IN_PROGRESS": "🔄", "COMPLETED": "✅", "FAILED": "❌"}
    emoji = status_emoji.get(new_status, "")
    
    response = f"{emoji} Updated goal status.\n\n**Goal:** {goal}\n**New Status:** {new_status}\n**Previous Status:** PENDING"
    
    if new_status == "FAILED":
        response += "\n\n⚠️ This goal has been marked as FAILED. It will appear in the CRITICAL ATTENTION section of the Goal Stimulus report. Would you like to retry or reassign it?"
    elif new_status == "COMPLETED":
        response += "\n\n🎉 Great progress! This goal is now complete."
    else:
        response += "\n\nThis goal is now the active focus in the Goal Stimulus report."
    
    return {"messages": [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": random.choice(user_msgs)},
        {"role": "assistant", "content": response}
    ]}

def generate_progress_query():
    project = random.choice(PROJECTS)
    num_total = random.randint(5, 12)
    num_completed = random.randint(0, num_total - 1)
    num_in_progress = random.randint(0, min(3, num_total - num_completed))
    num_failed = random.randint(0, min(2, num_total - num_completed - num_in_progress))
    num_pending = num_total - num_completed - num_in_progress - num_failed
    
    user_msgs = [
        "What's the progress?",
        "Show me the status of all goals",
        "How are we doing?",
        "Give me a progress report",
        "What's left to do?",
        "Status update please",
        "Show goal progress",
        "How many tasks are done?",
        f"What's the status of the {project['name']} project?",
    ]
    
    pct = int((num_completed / num_total) * 100)
    response_parts = [
        f"**Project Progress Report: {project['name']}**\n",
        f"Overall: {num_completed}/{num_total} goals complete ({pct}%)\n",
    ]
    
    if num_failed > 0:
        response_parts.append(f"🔴 **FAILED:** {num_failed} goal(s) need attention")
        for g in random.sample(project["goals"], min(num_failed, len(project["goals"]))):
            response_parts.append(f"   ! {g}")
    
    if num_in_progress > 0:
        response_parts.append(f"\n🔄 **IN PROGRESS:** {num_in_progress} goal(s) active")
        for g in random.sample(project["goals"], min(num_in_progress, len(project["goals"]))):
            response_parts.append(f"   > {g}")
    
    if num_pending > 0:
        response_parts.append(f"\n⏳ **PENDING:** {num_pending} goal(s) waiting")
        shown = min(num_pending, 5)
        for g in random.sample(project["goals"], min(shown, len(project["goals"]))):
            response_parts.append(f"   - {g}")
        if num_pending > 5:
            response_parts.append(f"   ... and {num_pending - 5} more")
    
    if num_completed > 0:
        response_parts.append(f"\n✅ **COMPLETED:** {num_completed} goal(s)")
    
    return {"messages": [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": random.choice(user_msgs)},
        {"role": "assistant", "content": "\n".join(response_parts)}
    ]}

def generate_prioritization():
    project = random.choice(PROJECTS)
    goals = random.sample(project["goals"], min(4, len(project["goals"])))
    
    user_msgs = [
        "What should I focus on next?",
        "Prioritize the remaining tasks",
        "What's the most important thing right now?",
        "Help me decide what to work on",
        "What's the priority order?",
    ]
    
    response_parts = ["Based on the current goal tree, here's the recommended priority:\n"]
    priorities = ["CRITICAL", "HIGH", "MEDIUM", "LOW"]
    for i, (goal, pri) in enumerate(zip(goals, priorities), 1):
        response_parts.append(f"{i}. **[{pri}]** {goal}")
    
    response_parts.append("\nRecommendation: Focus on the CRITICAL/HIGH items first. Failed goals take precedence over new work.")
    
    return {"messages": [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": random.choice(user_msgs)},
        {"role": "assistant", "content": "\n".join(response_parts)}
    ]}

def generate_bulk_create():
    project = random.choice(PROJECTS)
    goals = random.sample(project["goals"], random.randint(3, 6))
    
    user_msgs = [
        f"Create goals for the {project['name']} project: {', '.join(goals[:3])}...",
        f"I need to track multiple goals: {'; '.join(goals[:4])}",
        f"Set up the following goals: " + ", ".join(goals),
        f"Add these to the backlog: " + "; ".join(goals[:4]),
    ]
    
    response_parts = [f"Created {len(goals)} goals for **{project['name']}**:\n"]
    for i, g in enumerate(goals, 1):
        gid = str(uuid.uuid4())[:8]
        response_parts.append(f"{i}. [{gid}] {g} — PENDING")
    response_parts.append(f"\nAll goals initialized as PENDING. Use status updates to track progress.")
    
    return {"messages": [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": random.choice(user_msgs)},
        {"role": "assistant", "content": "\n".join(response_parts)}
    ]}

def generate_completion_check():
    user_msgs = [
        "Are all goals done?",
        "Is the project complete?",
        "Any blockers remaining?",
        "Can we ship this?",
        "Is everything finished?",
    ]
    
    scenarios = [
        "Not yet. There are 3 goals still IN_PROGRESS and 2 PENDING. The following need to be completed before we can consider the project done:\n\n1. 🔄 Implement payment processing\n2. 🔄 Add search and filtering\n3. 🔄 Set up email notifications\n4. ⏳ Implement inventory management\n5. ⏳ Create admin dashboard\n\nWould you like me to prioritize these?",
        "Almost! All goals are COMPLETED except one that is IN_PROGRESS:\n\n🔄 **Create reporting dashboard** — currently being worked on.\n\nOnce this is done, the project will be 100% complete.",
        "🎉 **All goals are COMPLETED!** The project is ready for release.\n\nFinal stats:\n- Total goals: 8\n- Completed: 8\n- Time tracked: Active for this session\n\nWould you like me to generate a summary report?",
        "⚠️ There's a blocker. One goal has FAILED status:\n\n❌ **Implement end-to-end encryption** — failed due to library compatibility issue.\n\nThis needs to be resolved before we can ship. Should I mark it for retry or create an alternative approach?",
    ]
    
    return {"messages": [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": random.choice(user_msgs)},
        {"role": "assistant", "content": random.choice(scenarios)}
    ]}

def generate_delete_goal():
    project = random.choice(PROJECTS)
    goal = random.choice(project["goals"])
    
    user_msgs = [
        f"Remove the goal '{goal}'",
        f"Delete '{goal}' from the tracker",
        f"We don't need '{goal}' anymore",
        f"Cancel the goal: {goal}",
    ]
    
    response = f"Removed goal from the tracker.\n\n**Deleted:** {goal}\n**Previous Status:** PENDING\n\nThe goal tree has been updated. This will no longer appear in the Goal Stimulus report."
    
    return {"messages": [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": random.choice(user_msgs)},
        {"role": "assistant", "content": response}
    ]}

def generate_goal_search():
    project = random.choice(PROJECTS)
    keyword = random.choice(["auth", "user", "test", "deploy", "api", "data", "search", "pay", "notification", "cache"])
    matching = [g for g in project["goals"] if keyword in g.lower()]
    if not matching:
        matching = [project["goals"][0]]
    
    user_msgs = [
        f"Find goals related to '{keyword}'",
        f"Search for '{keyword}' in goals",
        f"Which goals mention {keyword}?",
        f"Show me all {keyword}-related tasks",
    ]
    
    response_parts = [f"Found {len(matching)} goal(s) matching '{keyword}':\n"]
    for g in matching:
        status = random.choice(STATUSES)
        response_parts.append(f"- [{status}] {g}")
    
    return {"messages": [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": random.choice(user_msgs)},
        {"role": "assistant", "content": "\n".join(response_parts)}
    ]}

# Generate 10000 lines
lines = []
random.seed(42)

generators = [
    (generate_create_goal, 2500),
    (generate_decompose_goal, 1500),
    (generate_status_update, 2000),
    (generate_progress_query, 1500),
    (generate_prioritization, 800),
    (generate_bulk_create, 500),
    (generate_completion_check, 500),
    (generate_delete_goal, 400),
    (generate_goal_search, 300),
]

for gen_func, count in generators:
    for _ in range(count):
        lines.append(gen_func())

# Shuffle for training
random.shuffle(lines)

# Write JSONL
with open("C:/DevTools/rblab/mkpro/datajsonl/goaltracker_training.jsonl", "w", encoding="utf-8") as f:
    for line in lines:
        f.write(json.dumps(line, ensure_ascii=False) + "\n")

print(f"Generated {len(lines)} goaltracker training examples")
