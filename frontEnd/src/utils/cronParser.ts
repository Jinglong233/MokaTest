/**
 * 将 Cron 表达式解析为中文可读描述
 * @param cronExpression - Cron 表达式，支持 5 字段（分 时 日 月 周）或 6 字段（秒 分 时 日 月 周）
 * @returns 中文描述字符串
 */
export function parseCronToChinese(cronExpression: string): string {
    if (!cronExpression || typeof cronExpression !== "string") {
        return "无效的 Cron 表达式"
    }

    const parts = cronExpression.trim().split(/\s+/)

    // 只支持 6 字段格式
    if (parts.length !== 6) {
        return "不支持的 Cron 表达式格式（需要 6 字段：秒 分 时 日 月 星期）"
    }

    const [second, minute, hour, dayOfMonth, month, dayOfWeek] = parts

    // 每秒：* * * * * *
    if (second === "*" && minute === "*" && hour === "*" && dayOfMonth === "*" && month === "*" && dayOfWeek === "*") {
        return "每秒执行一次"
    }

    // 每分钟：30 * * * * * (第30秒执行)
    if (second !== "*" && minute === "*" && hour === "*" && dayOfMonth === "*" && month === "*" && dayOfWeek === "*") {
        return `每分钟的第 ${second} 秒执行`
    }

    // 每小时：0 30 * * * * (第30分钟执行)
    if (second === "0" && minute !== "*" && hour === "*" && dayOfMonth === "*" && month === "*" && dayOfWeek === "*") {
        return `每小时的第 ${minute} 分钟执行`
    }

    // 每周：0 0 9 * * 1,3,5 (周一、三、五 9:00)
    if (second === "0" && minute !== "*" && hour !== "*" && dayOfMonth === "*" && month === "*" && dayOfWeek !== "*") {
        const dayNames: Record<string, string> = {
            "0": "周日",
            "1": "周一",
            "2": "周二",
            "3": "周三",
            "4": "周四",
            "5": "周五",
            "6": "周六",
        }

        const days = dayOfWeek.split(",").map((d) => dayNames[d] || d)
        const timeStr = `${hour.padStart(2, "0")}:${minute.padStart(2, "0")}`

        return `每周 ${days.join("、")} 的 ${timeStr} 执行`
    }

    // 每月：0 0 9 15 * * (每月15号 9:00)
    if (second === "0" && minute !== "*" && hour !== "*" && dayOfMonth !== "*" && month === "*" && dayOfWeek === "*") {
        const timeStr = `${hour.padStart(2, "0")}:${minute.padStart(2, "0")}`
        return `每月 ${dayOfMonth} 号 ${timeStr} 执行`
    }

    // 每天：0 0 9 * * * (每天 9:00)
    if (second === "0" && minute !== "*" && hour !== "*" && dayOfMonth === "*" && month === "*" && dayOfWeek === "*") {
        const timeStr = `${hour.padStart(2, "0")}:${minute.padStart(2, "0")}`
        return `每天 ${timeStr} 执行`
    }

    // 其他复杂情况，提供基础解析
    return parseComplexCron(parts)
}

/**
 * 解析复杂的 Cron 表达式
 */
function parseComplexCron(parts: string[]): string {
    const [second, minute, hour, dayOfMonth, month, dayOfWeek] = parts
    const segments: string[] = []

    // 月份
    if (month !== "*") {
        if (month.includes(",")) {
            segments.push(`${month.replace(/,/g, "、")}月`)
        } else if (month.includes("-")) {
            segments.push(`${month}月`)
        } else if (month.includes("/")) {
            const step = month.split("/")[1]
            segments.push(`每${step}个月`)
        } else {
            segments.push(`${month}月`)
        }
    }

    // 日期
    if (dayOfMonth !== "*" && dayOfWeek === "*") {
        if (dayOfMonth.includes(",")) {
            segments.push(`${dayOfMonth.replace(/,/g, "、")}号`)
        } else if (dayOfMonth.includes("-")) {
            segments.push(`${dayOfMonth}号`)
        } else if (dayOfMonth.includes("/")) {
            const step = dayOfMonth.split("/")[1]
            segments.push(`每${step}天`)
        } else {
            segments.push(`${dayOfMonth}号`)
        }
    }

    // 星期
    if (dayOfWeek !== "*" && dayOfMonth === "*") {
        const dayNames: Record<string, string> = {
            "0": "周日",
            "1": "周一",
            "2": "周二",
            "3": "周三",
            "4": "周四",
            "5": "周五",
            "6": "周六",
        }

        if (dayOfWeek.includes(",")) {
            const days = dayOfWeek.split(",").map((d) => dayNames[d] || d)
            segments.push(days.join("、"))
        } else if (dayOfWeek.includes("-")) {
            const [start, end] = dayOfWeek.split("-")
            segments.push(`${dayNames[start]}-${dayNames[end]}`)
        } else if (dayOfWeek.includes("/")) {
            const step = dayOfWeek.split("/")[1]
            segments.push(`每${step}周`)
        } else {
            segments.push(dayNames[dayOfWeek] || dayOfWeek)
        }
    }

    // 时间
    if (hour !== "*") {
        if (hour.includes("/")) {
            const step = hour.split("/")[1]
            segments.push(`每${step}小时`)
        } else if (minute !== "*") {
            segments.push(`${hour.padStart(2, "0")}:${minute.padStart(2, "0")}`)
        } else {
            segments.push(`${hour}时`)
        }
    } else if (minute !== "*") {
        if (minute.includes("/")) {
            const step = minute.split("/")[1]
            segments.push(`每${step}分钟`)
        } else {
            segments.push(`第${minute}分钟`)
        }
    }

    // 秒
    if (second !== "*" && second !== "0") {
        if (second.includes("/")) {
            const step = second.split("/")[1]
            segments.push(`每${step}秒`)
        } else {
            segments.push(`第${second}秒`)
        }
    }

    return segments.length > 0 ? segments.join(" ") + " 执行" : "执行"
}

/**
 * 示例用法和测试
 */
export function testCronParser() {
    const examples = [
        { cron: "* * * * * *", desc: "每秒" },
        { cron: "30 * * * * *", desc: "每分钟第30秒" },
        { cron: "0 41 * * * *", desc: "每小时第41分钟" },
        { cron: "0 0 9 * * *", desc: "每天9:00" },
        { cron: "0 30 14 * * *", desc: "每天14:30" },
        { cron: "0 0 9 * * 1,3,5", desc: "周一三五9:00" },
        { cron: "0 0 9 15 * *", desc: "每月15号9:00" },
        { cron: "*/5 * * * * *", desc: "每5秒" },
        { cron: "0 */10 * * * *", desc: "每10分钟" },
        { cron: "0 0 */2 * * *", desc: "每2小时" },
    ]

    console.log("Cron 表达式解析测试：\n")
    examples.forEach(({ cron, desc }) => {
        const result = parseCronToChinese(cron)
        console.log(`${cron.padEnd(20)} | 期望: ${desc.padEnd(20)} | 结果: ${result}`)
    })
}
