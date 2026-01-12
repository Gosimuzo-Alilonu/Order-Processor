package processor;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;

public class OrdersProcessor {
	private TreeMap<String, Object[]> itemPrice;
	private TreeMap<String, String> orders;
	private Set<String> orderedItems;
	public ArrayList<Double> grandSum;

	public OrdersProcessor() {
	}

	public OrdersProcessor(TreeMap<String, Object[]> map, Set<String> ordered, TreeMap<String, String> orders,
			ArrayList<Double> grandSum) {
		itemPrice = map;
		orderedItems = ordered;
		this.orders = orders;
		this.grandSum = grandSum;
	}

	public void run(PrintWriter write, String dataFile, boolean multiple, int numOrders, String baseFilename,
			String resultsFile, int processNum) {
		DecimalFormat double0 = new DecimalFormat("#,###.00"); // adds the trailing 0s
		try {
			addDataToMapSet(itemPrice, orderedItems, dataFile);

			Scanner reader = new Scanner(new BufferedReader(new FileReader(baseFilename + processNum + ".txt")));
			ArrayList<CustomerItem> list = new ArrayList<>();
			String clientId = reader.nextLine();
			clientId = clientId.substring(clientId.indexOf(":") + 2);
			while (reader.hasNextLine()) {
				String item = reader.next();
				if (!CustomerItem.isFound(list, item)) {
					list.add(new OrdersProcessor().new CustomerItem(item, 1));
				}
				reader.nextLine();
			}
			reader.close();
			Collections.sort(list);
			orderDetails(clientId, list, double0);
		} catch (IOException e) {
			System.err.println(e.getMessage());
		}
	}

	private synchronized void orderDetails(String clientId, ArrayList<CustomerItem> list, DecimalFormat d0) {
		orders.put(clientId, "----- Order details for client with Id: " + clientId + " -----\n");
		double total = 0;
		for (int j = 0; j < list.size(); j++) {
			CustomerItem c = list.get(j);
			String name = c.getItem();
			double perItemCost = (Double) itemPrice.get(name)[0];
			int quantity = c.getNum();
			double totCost = perItemCost * quantity;
			total += totCost;

			String prev = orders.get(clientId) == null ? "" : orders.get(clientId);
			orders.put(clientId, prev + "Item's name: " + name + ", " + "Cost per item: $" + d0.format(perItemCost)
					+ ", Quantity: " + quantity + ", Cost: $" + d0.format(totCost) + "\n");

			Object[] updateQuant = { perItemCost, (Integer) itemPrice.get(name)[1] + quantity };
			itemPrice.put(name, updateQuant);
		}
		grandSum.set(0, grandSum.get(0) + total);
		orders.put(clientId, orders.get(clientId) + "Order Total: $" + d0.format(total) + "\n");

	}

	private synchronized void addDataToMapSet(TreeMap<String, Object[]> map, Set<String> ordered, String dataFile) {
		try {

			Scanner mapData = new Scanner(new BufferedReader(new FileReader(dataFile)));
			while (mapData.hasNext()) {
				String item = mapData.next();
				Object quant = itemPrice.get(item) == null ? 0 : itemPrice.get(item)[1];
				Object[] arr = { mapData.nextDouble(), quant };
				map.put(item, arr);
				ordered.add(item);
			}
			mapData.close();
		} catch (IOException e) {
			System.err.println(e.getMessage());
		}
	}

	private void getSummary(PrintWriter write, Set<String> orderedItems, TreeMap<String, Object[]> itemPrice,
			DecimalFormat double0, ArrayList<Double> grandSum) {
		for (int i = 0; i < orders.size(); i++) {
			int clientNum = 1001 + i;
			String clientId = Integer.toString(clientNum);
			System.out.println("Reading order for client with id: " + clientId);
			write.print((orders.get(clientId)));
		}
		write.println("***** Summary of all orders *****");
		Object[] fullItemsList = orderedItems.toArray();
		for (int i = 0; i < fullItemsList.length; i++) {
			String name = fullItemsList[i].toString();
			double costPer = (Double) itemPrice.get(name)[0];
			int sold = (Integer) itemPrice.get(name)[1];
			double itemTot = costPer * sold;

			if (sold != 0) {
				write.println("Summary - Item's name: " + fullItemsList[i].toString() + ", Cost per item: $"
						+ double0.format(costPer) + ", Number sold: " + sold + ", Item's Total: $"
						+ double0.format(itemTot));
			}
		}
		write.println("Summary Grand Total: $" + double0.format(grandSum.get(0)));
		write.close();
	}

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

		public int compareTo(CustomerItem e) {
			return item.compareTo(e.item);
		}

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
			op.run(write, dataFile, multiple, numOrders, baseFile, resultFile, processNum);
		}

	}

	public static void main(String[] args) throws InterruptedException {
		Scanner user = new Scanner(System.in);
		System.out.println("Enter item's data file name:");
		String dataFile = user.nextLine();
		System.out.println("Enter 'y' for multiple threads, any other character otherwise: ");
		boolean multiple = user.nextLine().equals("y") ? true : false;
		System.out.println("Enter number of orders to process: ");
		int numOrders = user.nextInt();
		user.nextLine();
		System.out.println("Enter order's base filename: ");
		String baseFile = user.next();
		System.out.println("Enter result's filename: ");
		String resultsFile = user.next();
		user.close();

		ArrayList<Double> grandSum = new ArrayList<>();
		ArrayList<String> ids = new ArrayList<>();
		grandSum.add(0.0);
		long startTime = System.currentTimeMillis();
		TreeMap<String, Object[]> itemPrice = new TreeMap<String, Object[]>();
		TreeMap<String, String> orders = new TreeMap<String, String>();
		Set<String> orderedItems = new TreeSet<String>();
		OrdersProcessor op = new OrdersProcessor(itemPrice, orderedItems, orders, grandSum);
		try {
			PrintWriter write = new PrintWriter(new FileWriter(resultsFile, false));

			if (multiple) {
				Thread[] threadCount = new Thread[numOrders];
				for (int i = 0; i < numOrders; i++) {
					Runnable mission = op.new Action(write, dataFile, multiple, numOrders, baseFile, resultsFile, op,
							i + 1);
					threadCount[i] = new Thread(mission);
					threadCount[i].start();
				}
				for (Thread t : threadCount) {
					t.join();
				}
				op.getSummary(write, orderedItems, itemPrice, new DecimalFormat("#,###.00"), grandSum);
			} else {
				for (int i = 1; i <= numOrders; i++) {
					op.run(write, dataFile, multiple, numOrders, baseFile, resultsFile, i);
				}
				op.getSummary(write, orderedItems, itemPrice, new DecimalFormat("#,###.00"), grandSum);
			}

		} catch (IOException e) {
			System.err.println(e.getMessage());
		}

		long endTime = System.currentTimeMillis();
		System.out.println("Results can be found in the file: " + resultsFile);
		System.out.println("Processing time (msec): " + (endTime - startTime));

	}

}