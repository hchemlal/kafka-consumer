<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>API Failure Alert - Kafka Consumer</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            line-height: 1.6;
            color: #333;
            max-width: 800px;
            margin: 0 auto;
            padding: 20px;
        }
        .header {
            background-color: #f44336;
            color: white;
            padding: 10px 20px;
            border-radius: 4px;
            margin-bottom: 20px;
        }
        .content {
            background-color: #f9f9f9;
            padding: 20px;
            border-radius: 4px;
            border: 1px solid #ddd;
        }
        .section {
            margin-bottom: 20px;
        }
        .section-title {
            font-weight: bold;
            color: #666;
            margin-bottom: 10px;
        }
        .error-message {
            color: #f44336;
            font-weight: bold;
        }
        .record-details {
            background-color: #fff;
            padding: 15px;
            border-radius: 4px;
            border: 1px solid #ddd;
        }
        .timestamp {
            color: #666;
            font-size: 0.9em;
            margin-top: 20px;
        }
        .footer {
            margin-top: 20px;
            padding-top: 20px;
            border-top: 1px solid #ddd;
            font-size: 0.9em;
            color: #666;
        }
    </style>
</head>
<body>
    <div class="header">
        <h2>⚠️ API Failure Alert</h2>
    </div>
    
    <div class="content">
        <div class="section">
            <div class="section-title">Error Message:</div>
            <div class="error-message">${errorMessage}</div>
        </div>

        <div class="section">
            <div class="section-title">Record Details:</div>
            <div class="record-details">
                <table>
                    <tr>
                        <td><strong>Topic:</strong></td>
                        <td>${record.topic}</td>
                    </tr>
                    <tr>
                        <td><strong>Partition:</strong></td>
                        <td>${record.partition}</td>
                    </tr>
                    <tr>
                        <td><strong>Offset:</strong></td>
                        <td>${record.offset}</td>
                    </tr>
                    <tr>
                        <td><strong>Key:</strong></td>
                        <td>${record.key!''}</td>
                    </tr>
                    <tr>
                        <td><strong>Timestamp:</strong></td>
                        <td>${record.timestamp}</td>
                    </tr>
                </table>
            </div>
        </div>

        <div class="section">
            <div class="section-title">Stack Trace:</div>
            <pre style="background-color: #fff; padding: 10px; border-radius: 4px; overflow-x: auto;">
${stackTrace}
            </pre>
        </div>

        <div class="timestamp">
            Alert generated at: ${alertTime}
        </div>
    </div>

    <div class="footer">
        This is an automated alert from the Kafka Consumer Application.
        Please investigate and take necessary action.
    </div>
</body>
</html>
