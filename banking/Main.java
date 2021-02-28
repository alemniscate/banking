package banking;

import java.util.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.sqlite.SQLiteDataSource;

public class Main {
    public static void main(String[] args) {
        Arguments arguments = new Arguments(args);
        String dbFileName = arguments.get("-fileName", "card.db");
        if (dbFileName.isEmpty()) {
            System.out.println("no db filename argument");
            return;
        }

        SqlDb db = new SqlDb(dbFileName);
        if (!db.connect()) {
            System.out.println("db connect error");
            return;
        }
    
        Scanner scanner = new Scanner(System.in);
        Action ac = new Action(scanner, db);

        while (true) {
            System.out.println("1. Create an account");
            System.out.println("2. Log into account");
            System.out.println("0. Exit");

            int menuno = Integer.parseInt(scanner.nextLine());
            System.out.println();
            switch (menuno) {
                case 1:
                    ac.createAccount();
                    break;
                case 2:
                    int exitcode = ac.login();
                    if (exitcode == 0) {
                        ac.exit();
                        menuno = 0;
                    }
                    break;
                case 0:
                    ac.exit();
                    break;
            }
            if (menuno == 0) {
                break;
            }
            System.out.println();
        }

        db.close();

        scanner.close();
    }
}

class Action {

    Scanner scanner;
    Random random;
    SqlDb db;

    Action(Scanner scanner, SqlDb db) {
        this.scanner = scanner;
        this.db = db;
        random = new Random(0);
    }

    void createAccount() {
        Account account = new Account(random);
        while (db.containsKey(account.cardNumber)) {
            account = new Account(random);
        }

        db.insert(account);
        System.out.println("Your card has been created");
        System.out.println("Your card number:");
        System.out.println(account.getCardNumber());
        System.out.println("Your card PIN:");
        System.out.println(account.getPin());        
    }

    int login() {
        System.out.println("Enter your card number:"); 
        String cardNumber = scanner.nextLine();
        System.out.println("Enter your PIN:");
        String pin = scanner.nextLine();
        System.out.println();
        Account account = db.get(cardNumber);
        if (account == null) {
            System.out.println("Wrong card number or PIN!");
            return -1;
        }
        if (!pin.equals(account.getPin())) {
            System.out.println("Wrong card number or PIN!");
            return -1;
        }
        System.out.println("You have successfully logged in!");
        System.out.println();
        int exitcode = account.query(scanner, db);
        return exitcode;
    }

    void exit() {
        System.out.println("Bye!");
    }
}

class Account {

    int id;
    String cardNumber;
    String pin;
    int balance;
    Random random;

    Account(int id, String cardNumber, String pin, int balance) {
        this.id = id;
        this.cardNumber = cardNumber;
        this.pin = pin;
        this.balance = balance;
    }

    Account(Random random) {
        this.random = random;
        id = getRandom9();
        cardNumber = String.format("400000%09d", id);
        cardNumber += getCheckDigit();
        pin = String.format("%04d", getRandom4());
        balance = 0;
    }

    int getId() {
        return id;
    }

    String getCardNumber() {
        return cardNumber;
    }

    String getPin() {
        return pin;
    }

    int getBalance() {
        return balance;
    }

    void setBalance(int balance) {
        this.balance = balance;
    }

    int getRandom9() {
        return random.nextInt(1_000_000_000);
    }

    int getRandom4() {
        return random.nextInt(10_000);
    }

    int getRandom1() {
        return random.nextInt(10);
    }

    int getCheckDigit(String cardNumber) {
        int sum = 0;
        for (int i = 0; i < 15; i++) {
            int digit = cardNumber.charAt(i) - '0';
            if (i % 2 == 0) {
                digit *= 2;
                digit = digit > 9 ? digit - 9 : digit;
            } 
            sum += digit;
        }
        return sum % 10 == 0 ? 0 : 10 - (sum % 10);
    }

    int getCheckDigit() {
        return getCheckDigit(this.cardNumber);
    }

    int query(Scanner scanner, SqlDb db) {

        while (true) {
            System.out.println("1. Balance");
            System.out.println("2. Add income");
            System.out.println("3. Do transfer");
            System.out.println("4. Close account");
            System.out.println("5. Log out");
            System.out.println("0. Exit");

            String input = scanner.nextLine();
            if (!input.matches("[0-5]")) {
                System.out.println();
                continue;
            }
            int menuno = Integer.parseInt(input);
            System.out.println();
            switch (menuno) {
                case 1:
                    queryBalance();
                    break;
                case 2:
                    addIncome(scanner, db);
                    break;
                case 3:
                    transfer(scanner, db);
                    break;
                case 4:
                    closeAccount(db);
                    break;
                case 5:
                    logout();
                    break;
                case 0:
                    exit();
                    break;
            }
            if (menuno == 0 || menuno == 5) {
                return menuno;
            }
            System.out.println();
        }
    }

    void queryBalance() {
        System.out.println("Balance: " + getBalance());
    }

    void addIncome(Scanner scanner, SqlDb db) {
        System.out.println("Enter income:");
        balance += Integer.parseInt(scanner.nextLine());
        if (db.update(this)) {
            System.out.println("Income was added!");
        }
    }

    void transfer(Scanner scanner, SqlDb db) {
    /*
        If the user tries to transfer more money than he/she has, output: Not enough money!
        If the user tries to transfer money to the same account, output the following message: You can't transfer money to the same account!
        If the receiver's card number doesn’t pass the Luhn algorithm, you should output: Probably you made a mistake in the card number. Please try again!
        If the receiver's card number doesn’t exist, you should output: Such a card does not exist.
        If there is no error, ask the user how much money they want to transfer and make the transaction.
    */
        System.out.println("Enter card number:");
        String destCardNumber = scanner.nextLine();
        if (destCardNumber.equals(cardNumber)) {
            System.out.println("You can't transfer money to the same account!");
            return;        
        }
        int checkDigit = getCheckDigit(destCardNumber.substring(0, 15));
        if (checkDigit != Integer.parseInt(destCardNumber.substring(15, 16))) {
            System.out.println("Probably you made a mistake in the card number. Please try again!");
            return;
        }
        Account destAccount = db.get(destCardNumber);
        if (destAccount == null) {
            System.out.println("Such a card does not exist.");
            return;
        }
        System.out.println("Enter how much money you want to transfer:");
        int amount = Integer.parseInt(scanner.nextLine());
        if (amount > balance) {
            System.out.println("Not enough money!");
            return;
        }
        if (!db.transfer(this, destAccount, amount)) {
            return;
        }
        System.out.println("Success!");
    }

    void closeAccount(SqlDb db) {
        if (db.delete(this)) {
            System.out.println("The account has been closed!");
        }
    }

    void logout() {
        System.out.println("You have successfully logged out!");
    }

    void exit() {
    }
}

class Arguments {

    Map<String, String> argMap;

    Arguments(String[] args) {
        
        List<String> argList = Arrays.asList(args);
        argMap = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            argMap.put(argList.get(i), argList.get(i + 1));
        }
    }

    String get(String key, String defaultValue) {
        if (argMap.isEmpty()) {
            return defaultValue;
        }

        if (argMap.get(key) == null) {
            return defaultValue;
        }
        
        return argMap.get(key);
    }
}

class SqlDb {

    String url;
    SQLiteDataSource dataSource;
    Connection con;

    SqlDb(String dbFileName) {
        url = "jdbc:sqlite:" + dbFileName;
        dataSource = new SQLiteDataSource();
        dataSource.setUrl(url);
    }

    boolean create() {   
        try { 
            Statement statement = con.createStatement();
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS card(" +
                "id INTEGER PRIMARY KEY, " +
                "number TEXT, " +
                "pin TEXT, " +
                "balance INTEGER DEFAULT 0);"
                );
            return true;    
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    boolean connect() {
        try {
            con = dataSource.getConnection();
            con.setAutoCommit(false);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }

        return create();
    }

    void close() {
        try {
            con.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    boolean execute(String sql) {
        try { 
            Statement statement = con.createStatement();
            statement.executeUpdate(sql);
            con.commit();
            return true;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            try {
                con.rollback();
            } catch (SQLException e1) {
                System.out.println(e1.getMessage());
            }
            return false;
        }
    }

    int toId(String cardNumber) {
        return Integer.parseInt(cardNumber.substring(6, 15));
    }

    boolean insert(Account account) {
        String sql = String.format("INSERT INTO card VALUES (%d, %s, %s, %d);",
            account.getId(), account.getCardNumber(), account.getPin(), account.getBalance());
        return execute(sql);
    }

    boolean update(Account account) {
        String sql = String.format("UPDATE card SET number = %s, pin = %s, balance = %d WHERE id = %d;",
               account.getCardNumber(), account.getPin(), account.getBalance(), account.getId());
        return execute(sql);
    }
    
    boolean delete(Account account) {
        String sql = String.format("DELETE FROM card WHERE id = %d;", account.getId());
        return execute(sql);
    }

    ResultSet select(String sql) {
        try { 
            Statement statement = con.createStatement();
            ResultSet result = statement.executeQuery(sql);
            return result;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    boolean containsKey(String cardNumber) {
        int id = toId(cardNumber);
        String sql = String.format("SELECT * FROM card WHERE id = %d;", id);
        ResultSet rs = select(sql);
        try {
            return rs.next();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    void put(Account account) {
        if (containsKey(account.getCardNumber())) {
            update(account);
        } else {
            insert(account);
        }
    }

    Account get(String cardNumber) {
        int id = toId(cardNumber);
        String sql = String.format("SELECT * FROM card WHERE id = %d", id);
        ResultSet rs = select(sql);
        try {
            while(rs.next()) {
                if (cardNumber.equals(rs.getString("number"))) {
                    Account account = new Account(rs.getInt("id"), rs.getString("number"), rs.getString("pin"), rs.getInt("balance"));
                    return account;
                }
            }
            return null;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    boolean remove(String cardNumber) {
        String sql = String.format("DELETE FROM card WHERE id = %s;", cardNumber);
        return execute(sql);
    }

    boolean tranUpdate(Account source, Account destination) {
        String sourceSql = "UPDATE card SET balance = ? WHERE id = ?;";
        String destSql = "UPDATE card SET balance = ? WHERE id = ?;";
        try (PreparedStatement sourcePs = con.prepareStatement(sourceSql);
            PreparedStatement destPs = con.prepareStatement(destSql)) {

            sourcePs.setInt(1, source.getBalance());
            sourcePs.setInt(2, source.getId());
            destPs.setInt(1, destination.getBalance());
            destPs.setInt(2, destination.getId());

            sourcePs.executeUpdate();
            destPs.executeUpdate();

            con.commit();
            return true;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            try {
                con.rollback();
            } catch (SQLException e1) {
                System.out.println(e1.getMessage());
            }
            return false;
        }
    }

    boolean transfer(Account source, Account destination, int amount) {
        source.setBalance(source.getBalance() - amount);
        destination.setBalance(destination.getBalance() + amount);
        return tranUpdate(source, destination);
    }
}
 