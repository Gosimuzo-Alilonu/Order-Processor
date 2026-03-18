package processor;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;

public class OrdersProcessor {

    // Maps item name → [price, total quantity sold]
    private TreeMap<String, Object[]> itemPrice;

    // Maps client ID → formatted order details string
    private TreeMap<String, String> orders;

    // Stores all unique item names
    private Set<String> orderedItems;

    // Stores overall total revenue (index 0 used as accumulator)
    public ArrayList<Double> grandSum;

    public OrdersProcessor() {
    }

    // Constructor initializes shared data structures
    public OrdersProcessor(TreeMap<String, Object[]> map, Set<String> ordered, TreeMap<String, String> orders,
            ArrayList<Double> grandSum) {
        itemPrice = map;
        orderedItems = ordered;
        this.orders = orders;
        this.grandSum = grandSum;
    }

    /**
     * Main processing method for a single order file
     * Reads item data, processes a client's order, and calculates totals
     */
    public void run(PrintWriter write, String dataFile, boolean multiple, int numOrders, String baseFilename,
            String resultsFile, int processNum) {

        // Formatter to ensure currency always shows 2 decimal places
        DecimalFormat double0 = new DecimalFormat("#,###.00");

        try {
            // Load item prices and initialize quantities
            addDataToMapSet(itemPrice, orderedItems, dataFile);

            // Read a specific order file (e.g., orders1.txt, orders2.txt, etc.)
            Scanner reader = new Scanner(new BufferedReader(new FileReader(baseFilename + processNum + ".txt")));

            ArrayList<CustomerItem> list = new ArrayList<>();

            // Extract client ID from first line
            String clientId = reader.nextLine();
            clientId = clientId.substring(clientId.indexOf(":") + 2);

            // Read each item in the order
            while (reader.hasNextLine()) {
                String item = reader.next();

                // If item not already in list, add it; otherwise increment quantity
                if (!CustomerItem.isFound(list, item)) {
                    list.add(new OrdersProcessor().new CustomerItem(item, 1));
                }

                reader.nextLine(); // move to next line
            }

            reader.close();

            // Sort items alphabetically
            Collections.sort(list);

            // Generate detailed order summary
            orderDetails(clientId, list, double0);

        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    /**
     * Builds detailed order summary for a client
     * Also updates global totals and item quantities
     */
    private synchronized void orderDetails(String clientId, ArrayList<CustomerItem> list, DecimalFormat d0) {

        // Initialize client order header
        orders.put(clientId, "----- Order details for client with Id: " + clientId + " -----\n");

        double total = 0;

        for (int j = 0; j < list.size(); j++) {
            CustomerItem c = list.get(j);

            String name = c.getItem();
            double perItemCost = (Double) itemPrice.get(name)[0];
            int quantity = c.getNum();

            double totCost = perItemCost * quantity;
            total += totCost;

            // Append item details to client's order string
            String prev = orders.get(clientId) == null ? "" : orders.get(clientId);
            orders.put(clientId, prev + "Item's name: " + name + ", " +
                    "Cost per item: $" + d0.format(perItemCost)
                    + ", Quantity: " + quantity
                    + ", Cost: $" + d0.format(totCost) + "\n");

            // Update total quantity sold for this item
            Object[] updateQuant = { perItemCost, (Integer) itemPrice.get(name)[1] + quantity };
            itemPrice.put(name, updateQuant);
        }

        // Update global revenue total
        grandSum.set(0, grandSum.get(0) + total);

        // Append order total
        orders.put(clientId, orders.get(clientId) + "Order Total: $" + d0.format(total) + "\n");
    }

    /**
     * Loads item prices from file into map and initializes quantities
     */
    private synchronized void addDataToMapSet(TreeMap<String, Object[]> map, Set<String> ordered, String dataFile) {
        try {
            Scanner mapData = new Scanner(new BufferedReader(new FileReader(dataFile)));

            while (mapData.hasNext()) {
                String item = mapData.next();

                // Preserve existing quantity if item already exists
                Object quant = itemPrice.get(item) == null ? 0 : itemPrice.get(item)[1];

                // Store price and quantity
                Object[] arr = { mapData.nextDouble(), quant };
                map.put(item, arr);

                // Track unique items
                ordered.add(item);
            }

            mapData.close();

        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    /**
     * Writes all client orders and overall summary to output file
     */
    private void getSummary(PrintWriter write, Set<String> orderedItems, TreeMap<String, Object[]> itemPrice,
            DecimalFormat double0, ArrayList<Double> grandSum) {

        // Print each client's order
        for (int i = 0; i < orders.size(); i++) {
            int clientNum = 1001 + i;
            String clientId = Integer.toString(clientNum);

            System.out.println("Reading order for client with id: " + clientId);
            write.print((orders.get(clientId)));
        }

        write.println("***** Summary of all orders *****");

        // Convert set to array for iteration
        Object[] fullItemsList = orderedItems.toArray();

        for (int i = 0; i < fullItemsList.length; i++) {
            String name = fullItemsList[i].toString();

            double costPer = (Double) itemPrice.get(name)[0];
            int sold = (Integer) itemPrice.get(name)[1];

            double itemTot = costPer * sold;

            // Only print items that were sold
            if (sold != 0) {
                write.println("Summary - Item's name: " + name +
                        ", Cost per item: $" + double0.format(costPer) +
                        ", Number sold: " + sold +
                        ", Item's Total: $" + double0.format(itemTot));
            }
        }

        // Print grand total
        write.println("Summary Grand Total: $" + double0.format(grandSum.get(0)));

        write.close();
    }

    /**
     * Represents an item in a customer's order
     * Stores item name and quantity
     */
    public class CustomerItem implements Comparable<CustomerItem> {
        String item;
        int quantity;

        public CustomerItem(String item, int num) {
            this.item = item;
            quantity = num;
        }

        public String getItem() {
            return item;
        }

        public int getNum() {
            return quantity;
        }

        public void setNum(int num) {
            quantity = num;
        }

        // Enables sorting items alphabetically
        public int compareTo(CustomerItem e) {
            return item.compareTo(e.item);
        }

        /**
         * Checks if item already exists in list
         * If yes → increment quantity
         */
        public static boolean isFound(ArrayList<CustomerItem> list, String item) {
            for (int i = 0; i < list.size(); i++) {
                CustomerItem c = list.get(i);

                if (c.getItem().equals(item)) {
                    c.setNum(++c.quantity);
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Runnable class to support multithreaded order processing
     */
    public class Action implements Runnable {
        private String dataFile;
        private boolean multiple;
        private int numOrders;
        private String baseFile;
        private String resultFile;
        private int processNum;
        private PrintWriter write;
        OrdersProcessor op;

        public Action(PrintWriter write, String dataFile, boolean multiple, int numOrders, String baseFile,
                String resultFile, OrdersProcessor op, int num) {

            this.dataFile = dataFile;
            this.multiple = multiple;
            this.numOrders = numOrders;
            this.baseFile = baseFile;
            this.resultFile = resultFile;
            this.write = write;
            this.op = op;
            processNum = num;
        }

        @Override
        public void run() {
            // Each thread processes a separate order file
            op.run(write, dataFile, multiple, numOrders, baseFile, resultFile, processNum);
        }
    }

    /**
     * Entry point of the program
     * Handles user input and controls single vs multithreaded execution
     */
    public static void main(String[] args) throws InterruptedException {

        Scanner user = new Scanner(System.in);

        // Collect user inputs
        System.out.println("Enter item's data file name:");
        String dataFile = user.nextLine();

        System.out.println("Enter 'y' for multiple threads, any other character otherwise: ");
        boolean multiple = user.nextLine().equals("y");

        System.out.println("Enter number of orders to process: ");
        int numOrders = user.nextInt();
        user.nextLine();

        System.out.println("Enter order's base filename: ");
        String baseFile = user.next();

        System.out.println("Enter result's filename: ");
        String resultsFile = user.next();

        user.close();

        // Initialize shared data structures
        ArrayList<Double> grandSum = new ArrayList<>();
        grandSum.add(0.0);

        long startTime = System.currentTimeMillis();

        TreeMap<String, Object[]> itemPrice = new TreeMap<>();
        TreeMap<String, String> orders = new TreeMap<>();
        Set<String> orderedItems = new TreeSet<>();

        OrdersProcessor op = new OrdersProcessor(itemPrice, orderedItems, orders, grandSum);

        try {
            PrintWriter write = new PrintWriter(new FileWriter(resultsFile, false));

            if (multiple) {
                // MULTITHREADED execution
                Thread[] threadCount = new Thread[numOrders];

                for (int i = 0; i < numOrders; i++) {
                    Runnable mission = op.new Action(write, dataFile, multiple, numOrders, baseFile, resultsFile, op,
                            i + 1);

                    threadCount[i] = new Thread(mission);
                    threadCount[i].start();
                }

                // Wait for all threads to finish
                for (Thread t : threadCount) {
                    t.join();
                }

                op.getSummary(write, orderedItems, itemPrice, new DecimalFormat("#,###.00"), grandSum);

            } else {
                // SINGLE-THREADED execution
                for (int i = 1; i <= numOrders; i++) {
                    op.run(write, dataFile, multiple, numOrders, baseFile, resultsFile, i);
                }

                op.getSummary(write, orderedItems, itemPrice, new DecimalFormat("#,###.00"), grandSum);
            }

        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

        long endTime = System.currentTimeMillis();

        // Output results
        System.out.println("Results can be found in the file: " + resultsFile);
        System.out.println("Processing time (msec): " + (endTime - startTime));
    }
}
