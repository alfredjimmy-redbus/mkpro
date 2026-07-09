import json
import random
import itertools

# Coordinator training data generator
# Produces 10000 JSONL lines covering task delegation, routing, orchestration

SYSTEM_PROMPT = "You are the Coordinator agent for mkpro. You orchestrate a team of specialized AI agents. Your job is to understand user requests and delegate tasks to the appropriate specialist agent using ask_* tools. You do not write code or run commands yourself. Available agents: GoalTracker, Coder, CodeEditor, SysAdmin, GitAgent, Tester, DocWriter, SecurityAuditor, Architect, DatabaseAdmin, DevOps, DataAnalyst, AndroidDev, IosDev."

# Templates for diverse training examples
CODING_TASKS = [
    "Add a login page to the application",
    "Implement JWT authentication",
    "Create a REST API endpoint for user registration",
    "Add pagination to the products list",
    "Implement a search feature with filters",
    "Create a WebSocket connection for real-time updates",
    "Add input validation to the contact form",
    "Implement file upload functionality",
    "Create a caching layer for API responses",
    "Add rate limiting to the API endpoints",
    "Implement OAuth2 integration with Google",
    "Create a notification system",
    "Add dark mode toggle to the UI",
    "Implement lazy loading for images",
    "Create a dashboard with charts",
    "Add export to CSV functionality",
    "Implement undo/redo for the editor",
    "Create a plugin system",
    "Add multi-language support (i18n)",
    "Implement a queue-based job processor",
    "Add error boundary components",
    "Create a settings page",
    "Implement drag and drop for list items",
    "Add keyboard shortcuts",
    "Create a comment system",
    "Implement email verification flow",
    "Add audit logging",
    "Create a health check endpoint",
    "Implement graceful shutdown",
    "Add request/response compression",
]

TESTING_TASKS = [
    "Write unit tests for the UserService class",
    "Add integration tests for the payment flow",
    "Create E2E tests for the checkout process",
    "Write tests for the authentication middleware",
    "Add snapshot tests for the UI components",
    "Create load tests for the API",
    "Write tests for edge cases in the calculator",
    "Add regression tests for the bug we just fixed",
    "Create tests for the database migration",
    "Write performance benchmarks for the sorting algorithm",
    "Add tests for the error handling paths",
    "Create mock tests for external API calls",
    "Write contract tests for the microservices",
    "Add accessibility tests for the forms",
    "Create security tests for input sanitization",
]

DEVOPS_TASKS = [
    "Create a Dockerfile for this application",
    "Set up a CI/CD pipeline with GitHub Actions",
    "Deploy this to AWS ECS",
    "Configure Kubernetes manifests for production",
    "Set up monitoring with Prometheus and Grafana",
    "Create a docker-compose for local development",
    "Configure auto-scaling for the service",
    "Set up log aggregation with ELK stack",
    "Create a Terraform config for the infrastructure",
    "Configure SSL certificates for the domain",
    "Set up a staging environment",
    "Create a backup strategy for the database",
    "Configure CDN for static assets",
    "Set up blue-green deployment",
    "Create a disaster recovery plan",
]

GIT_TASKS = [
    "Commit all the changes we made",
    "Create a new branch for this feature",
    "Push the code to the repository",
    "Merge the feature branch into main",
    "Tag this release as v2.1.0",
    "Revert the last commit",
    "Show me the recent commit history",
    "Stage only the service files",
    "Create a release branch",
    "Cherry-pick the fix from the other branch",
]

DOC_TASKS = [
    "Update the README with the new API endpoints",
    "Generate JavaDoc for the service layer",
    "Document the deployment process",
    "Write API documentation for the new endpoints",
    "Update the changelog",
    "Create a getting started guide",
    "Document the configuration options",
    "Write a migration guide for v2 to v3",
    "Add inline comments to the complex algorithm",
    "Create architecture decision records",
]

SECURITY_TASKS = [
    "Scan the code for SQL injection vulnerabilities",
    "Check for hardcoded secrets in the codebase",
    "Audit the npm dependencies for known CVEs",
    "Review the authentication flow for weaknesses",
    "Check if we're properly sanitizing user input",
    "Analyze the CORS configuration",
    "Review the file upload security",
    "Check for XSS vulnerabilities in the templates",
    "Audit the API key management",
    "Review the session management implementation",
]

ARCHITECT_TASKS = [
    "Review the overall architecture of this project",
    "Suggest how to split this monolith into microservices",
    "Analyze the coupling between modules",
    "Design a scalable solution for real-time messaging",
    "Review the database schema design",
    "Suggest design patterns for this problem",
    "Analyze the performance bottlenecks",
    "Design an event-driven architecture",
    "Review the API contract design",
    "Plan the migration from REST to GraphQL",
]

DATABASE_TASKS = [
    "Write a migration script to add the orders table",
    "Optimize this slow query",
    "Design the schema for the new feature",
    "Create indexes for the frequently queried columns",
    "Write a data migration script",
    "Analyze the query performance",
    "Design a caching strategy for the database",
    "Create a stored procedure for the report",
    "Normalize the user_preferences table",
    "Write a rollback script for the migration",
]

SYSADMIN_TASKS = [
    "Run the test suite",
    "Build the project",
    "Check the disk space usage",
    "Install the project dependencies",
    "Start the development server",
    "Check the running processes",
    "Clear the build cache",
    "Run the linter on the source code",
    "Check the network connectivity",
    "List all environment variables",
]

DATA_TASKS = [
    "Analyze the sales data in the CSV file",
    "Generate a report on user engagement",
    "Find patterns in the error logs",
    "Calculate the conversion rate from the data",
    "Create a visualization of the monthly trends",
    "Analyze the A/B test results",
    "Clean and transform the raw dataset",
    "Generate statistics on API response times",
    "Find correlations in the user behavior data",
    "Create a summary of the quarterly metrics",
]

ANDROID_TASKS = [
    "Create a new Activity for the profile screen",
    "Implement the RecyclerView for the feed",
    "Add push notification support",
    "Create a Jetpack Compose component for the card",
    "Implement offline data caching with Room",
    "Set up navigation with Jetpack Navigation",
    "Create a custom view for the circular progress",
    "Implement biometric authentication",
    "Add deep linking support",
    "Create a widget for the home screen",
]

IOS_TASKS = [
    "Create a SwiftUI view for the settings screen",
    "Implement Core Data for local storage",
    "Add push notification handling",
    "Create a custom UICollectionViewLayout",
    "Implement Sign in with Apple",
    "Set up URL scheme handling",
    "Create an App Clip",
    "Implement background fetch",
    "Add accessibility labels to all views",
    "Create a share extension",
]

GOAL_TASKS = [
    "Track the progress of our current sprint",
    "What are the pending tasks?",
    "Mark the authentication feature as complete",
    "Create a goal for the v2.0 release",
    "What's the status of the refactoring task?",
    "Break down the payment integration into subtasks",
    "Update the goal for API development to in-progress",
    "How many tasks are remaining?",
    "Prioritize the bug fixes over new features",
    "Show me what failed recently",
]

MULTI_STEP = [
    "Refactor the user service, add tests, and update the docs",
    "Fix the login bug, write a test for it, and commit",
    "Design the new feature, implement it, and deploy to staging",
    "Audit the security, fix the issues, and create a report",
    "Analyze the performance, optimize the queries, and run benchmarks",
    "Create the API endpoint, add validation, write tests, and document it",
    "Set up the CI pipeline, add test stage, and configure deployment",
    "Review the architecture, propose changes, and create implementation plan",
]

CLARIFICATION_TASKS = [
    "Fix it",
    "Make it better",
    "Can you help?",
    "Something is wrong",
    "I need this done",
    "Update the thing",
    "Change the code",
    "Do the deployment",
]

# Agent routing map
def get_delegation(task_type, task):
    delegations = {
        "coding": ("ask_coder", "Coder"),
        "testing": ("ask_tester", "Tester"),
        "devops": ("ask_dev_ops", "DevOps"),
        "git": ("ask_git_agent", "GitAgent"),
        "docs": ("ask_doc_writer", "DocWriter"),
        "security": ("ask_security_auditor", "SecurityAuditor"),
        "architect": ("ask_architect", "Architect"),
        "database": ("ask_database_admin", "DatabaseAdmin"),
        "sysadmin": ("ask_sys_admin", "SysAdmin"),
        "data": ("ask_data_analyst", "DataAnalyst"),
        "android": ("ask_android_dev", "AndroidDev"),
        "ios": ("ask_ios_dev", "IosDev"),
        "goals": ("ask_goal_tracker", "GoalTracker"),
    }
    tool, agent = delegations[task_type]
    return tool, agent

def generate_single_delegation(task_type, task):
    tool, agent = get_delegation(task_type, task)
    response = f'I\'ll delegate this to the {agent}.\n\n[Calling {tool} with instruction: "{task}"]'
    return {"messages": [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": task},
        {"role": "assistant", "content": response}
    ]}

def generate_multi_step(task):
    steps = []
    if "refactor" in task.lower() or "implement" in task.lower() or "fix" in task.lower():
        steps.append(("ask_architect", "Architect", "analyze the current implementation"))
    if "code" in task.lower() or "implement" in task.lower() or "create" in task.lower() or "fix" in task.lower() or "refactor" in task.lower():
        steps.append(("ask_coder", "Coder", "implement the changes"))
    if "test" in task.lower():
        steps.append(("ask_tester", "Tester", "write and run tests"))
    if "doc" in task.lower():
        steps.append(("ask_doc_writer", "DocWriter", "update documentation"))
    if "deploy" in task.lower() or "staging" in task.lower():
        steps.append(("ask_dev_ops", "DevOps", "handle deployment"))
    if "commit" in task.lower():
        steps.append(("ask_git_agent", "GitAgent", "commit the changes"))
    if "security" in task.lower() or "audit" in task.lower():
        steps.append(("ask_security_auditor", "SecurityAuditor", "perform security review"))
    if "performance" in task.lower() or "optimize" in task.lower() or "benchmark" in task.lower():
        steps.append(("ask_coder", "Coder", "optimize the implementation"))
    if "architecture" in task.lower() or "design" in task.lower() or "review" in task.lower():
        steps.append(("ask_architect", "Architect", "review and design"))
    
    if not steps:
        steps = [("ask_coder", "Coder", "handle the implementation"), ("ask_tester", "Tester", "verify with tests")]
    
    response_parts = [f"This is a multi-step task. I'll coordinate the following agents:\n"]
    for i, (tool, agent, desc) in enumerate(steps, 1):
        response_parts.append(f"{i}. **{agent}** — {desc}")
    response_parts.append(f"\nLet me start with step 1.\n\n[Calling {steps[0][0]} with instruction: \"{task.split(',')[0].strip()}\"]")
    
    return {"messages": [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": task},
        {"role": "assistant", "content": "\n".join(response_parts)}
    ]}

def generate_clarification(task):
    response = f"I'd like to help, but I need more context. Could you please clarify:\n\n1. Which part of the project are you referring to?\n2. What specific outcome do you expect?\n3. Are there any constraints or preferences I should know about?\n\nThis will help me delegate to the right specialist."
    return {"messages": [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": task},
        {"role": "assistant", "content": response}
    ]}

def generate_status_query():
    queries = [
        "What agents are available?",
        "What can you do?",
        "How does this work?",
        "Who handles testing?",
        "Which agent should I use for database work?",
    ]
    answers = [
        "I have 14 specialist agents available: GoalTracker, Coder, CodeEditor, SysAdmin, GitAgent, Tester, DocWriter, SecurityAuditor, Architect, DatabaseAdmin, DevOps, DataAnalyst, AndroidDev, and IosDev. Just describe what you need, and I'll route it to the right expert.",
        "I can coordinate any software engineering task by delegating to my team of specialists. I handle coding, testing, deployment, security audits, documentation, database design, architecture review, and mobile development. Just tell me what you need.",
        "You give me a request, and I analyze it to determine which specialist agent(s) should handle it. I then delegate using ask_* tools. For complex tasks, I coordinate multiple agents in sequence.",
        "The Tester agent handles all testing tasks — unit tests, integration tests, E2E tests with Selenium, and test execution. Just ask me to test something and I'll delegate.",
        "The DatabaseAdmin agent handles SQL queries, schema design, migrations, and database optimization. Tell me what you need and I'll route it there.",
    ]
    idx = random.randint(0, len(queries) - 1)
    return {"messages": [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": queries[idx]},
        {"role": "assistant", "content": answers[idx]}
    ]}

def generate_follow_up():
    contexts = [
        ("The Coder agent has completed the implementation. Here's the result:\n\n```java\npublic class UserService { ... }\n```\n\nWhat would you like to do next?",
         "Great, now write tests for it",
         "I'll delegate the testing to the Tester agent.\n\n[Calling ask_tester with instruction: \"Write unit tests for the UserService class that was just implemented\"]"),
        ("The Tester reports all 15 tests pass.\n\nWhat's next?",
         "Commit and push",
         "I'll hand this to the GitAgent.\n\n[Calling ask_git_agent with instruction: \"Stage the new UserService and its tests, commit with a semantic message, and push to the current branch\"]"),
        ("The SecurityAuditor found 2 issues:\n1. SQL injection in getUserById\n2. Missing input validation in updateUser\n\nShould I fix these?",
         "Yes fix them",
         "I'll delegate the fixes to the CodeEditor.\n\n[Calling ask_code_editor with instruction: \"Fix SQL injection in getUserById by using parameterized queries, and add input validation to updateUser\"]"),
        ("The Architect suggests splitting the monolithic service into 3 microservices: UserService, OrderService, and PaymentService.\n\nShould I proceed with the implementation?",
         "Yes, start with UserService",
         "I'll have the Coder start implementing the UserService microservice based on the Architect's design.\n\n[Calling ask_coder with instruction: \"Implement the UserService microservice following the architecture design: separate user management into its own service with REST endpoints for CRUD operations\"]"),
    ]
    ctx, user_msg, assistant_msg = random.choice(contexts)
    return {"messages": [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": user_msg},
        {"role": "assistant", "content": assistant_msg}
    ]}

# Generate 10000 lines
lines = []
task_pools = [
    ("coding", CODING_TASKS),
    ("testing", TESTING_TASKS),
    ("devops", DEVOPS_TASKS),
    ("git", GIT_TASKS),
    ("docs", DOC_TASKS),
    ("security", SECURITY_TASKS),
    ("architect", ARCHITECT_TASKS),
    ("database", DATABASE_TASKS),
    ("sysadmin", SYSADMIN_TASKS),
    ("data", DATA_TASKS),
    ("android", ANDROID_TASKS),
    ("ios", IOS_TASKS),
    ("goals", GOAL_TASKS),
]

random.seed(42)

# 6000 single delegations (varied across all agent types)
for i in range(6000):
    task_type, tasks = random.choice(task_pools)
    task = random.choice(tasks)
    # Add variety with prefixes
    prefixes = ["", "Can you ", "Please ", "I need to ", "Help me ", "Let's ", "I want to ", "Could you "]
    suffixes = ["", "", "", " please", " for me", " right now", " when you get a chance", " asap"]
    modified_task = random.choice(prefixes) + task.lower() if random.random() > 0.5 else task
    modified_task += random.choice(suffixes)
    lines.append(generate_single_delegation(task_type, modified_task))

# 1500 multi-step tasks
for i in range(1500):
    task = random.choice(MULTI_STEP)
    lines.append(generate_multi_step(task))

# 800 clarification requests
for i in range(800):
    task = random.choice(CLARIFICATION_TASKS)
    lines.append(generate_clarification(task))

# 1000 follow-ups / context continuations
for i in range(1000):
    lines.append(generate_follow_up())

# 700 status/capability queries
for i in range(700):
    lines.append(generate_status_query())

# Shuffle for training
random.shuffle(lines)

# Write JSONL
with open("C:/DevTools/rblab/mkpro/datajsonl/coordinator_training.jsonl", "w", encoding="utf-8") as f:
    for line in lines:
        f.write(json.dumps(line, ensure_ascii=False) + "\n")

print(f"Generated {len(lines)} coordinator training examples")
