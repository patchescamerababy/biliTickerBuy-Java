//package com.example.geetest;
//
//import org.json.JSONObject;
//import static org.junit.Assert.assertNotNull;
//import static org.junit.Assert.assertTrue;
//import org.junit.Test;
//
//public class TripleValidatorTest {
//
//    @Test
//    public void testValidationProcess() {
//        int n = 10; // 测试次数，可以根据需要调整
//        int successCount = 0;
//        double totalTime = 0;
//
//        System.out.println("Starting validation test for " + n + " iterations...");
//
//        for (int i = 0; i < n; i++) {
//            System.out.println("--- Test " + (i + 1) + " ---");
//            long startTime = System.currentTimeMillis();
//
//            // 1. Register and get gt/challenge
//            String gtChallengeJson = TripleValidator.registerTest();
//            assertNotNull("Failed to get gt and challenge", gtChallengeJson);
//            JSONObject jsonObj = new JSONObject(gtChallengeJson);
//            String gt = jsonObj.getString("gt");
//            String challenge = jsonObj.getString("challenge");
//            System.out.println("Successfully registered. gt: " + gt);
//
//            // 2. Validate
//            String validateResult = TripleValidator.simpleMatchRetry(gt, challenge);
//            assertNotNull("Validation result is null", validateResult);
//
//            long endTime = System.currentTimeMillis();
//            double elapsedTime = (endTime - startTime) / 1000.0;
//            totalTime += elapsedTime;
//
//            if (validateResult != null && !validateResult.isEmpty()) {
//                successCount++;
//                System.out.printf("Test %d: Result = %s, Time = %.4fs%n", (i + 1), validateResult, elapsedTime);
//            } else {
//                System.err.printf("Test %d: FAILED! Result = %s, Time = %.4fs%n", (i + 1), validateResult, elapsedTime);
//            }
//        }
//
//        double accuracy = (double) successCount / n * 100;
//        double avgTime = totalTime / n;
//
//        System.out.println("\n✅ Testing complete. Total iterations: " + n);
//        System.out.printf("✅ Accuracy: %.2f%%%n", accuracy);
//        System.out.printf("✅ Average Time: %.4fs%n", avgTime);
//
//        // 断言成功率至少为80%，可以根据实际情况调整
//        assertTrue("Accuracy should be at least 80%", accuracy >= 80.0);
//    }
//}
