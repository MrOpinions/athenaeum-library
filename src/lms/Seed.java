package lms;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static lms.Circulation.L;

/** Demo data so the app is alive on first run: books, members, six months of loan history, fines, holds. */
final class Seed {
    /** Password for every demo account (documented in the README; demo data only). */
    static final String DEMO_PASSWORD = "Library@2026";

    // title | authors (;) | category | year | publisher | pages | description | copies | price
    private static final String[] BOOKS = {
        "To Kill a Mockingbird|Harper Lee|Fiction|1960|J. B. Lippincott|281|A young girl in the American South watches her father defend a man falsely accused.|3|399",
        "The Great Gatsby|F. Scott Fitzgerald|Fiction|1925|Scribner|180|A mysterious millionaire chases a lost love in Jazz Age New York.|2|299",
        "The Alchemist|Paulo Coelho|Fiction|1988|HarperOne|208|A shepherd boy travels in search of treasure and finds his own legend.|3|349",
        "Life of Pi|Yann Martel|Fiction|2001|Knopf Canada|319|A boy survives a shipwreck sharing a lifeboat with a Bengal tiger.|2|450",
        "The Kite Runner|Khaled Hosseini|Fiction|2003|Riverhead|372|Friendship, betrayal and redemption between two boys in Kabul.|2|425",
        "Pride and Prejudice|Jane Austen|Classics|1813|T. Egerton|279|Elizabeth Bennet and Mr Darcy learn to see past first impressions.|3|250|",
        "One Hundred Years of Solitude|Gabriel Garcia Marquez|Classics|1967|Harper & Row|417|Seven generations of the Buendia family in the town of Macondo.|2|499",
        "Frankenstein|Mary Shelley|Classics|1818|Lackington|280|A scientist's creation turns on him in this foundational gothic novel.|2|275",
        "Jane Eyre|Charlotte Bronte|Classics|1847|Smith, Elder & Co.|532|An orphan becomes a governess and finds a secret at Thornfield Hall.|2|299",
        "Crime and Punishment|Fyodor Dostoevsky|Classics|1866|The Russian Messenger|545|A student's murder and the guilt that follows him through St Petersburg.|2|399",
        "Things Fall Apart|Chinua Achebe|Classics|1958|Heinemann|209|A proud Igbo leader faces colonial change in late-19th-century Nigeria.|2|325",
        "1984|George Orwell|Science Fiction|1949|Secker & Warburg|328|A clerk in a surveillance state risks everything for private thought and love.|4|299",
        "Dune|Frank Herbert|Science Fiction|1965|Chilton Books|412|Politics, prophecy and spice on the desert planet Arrakis.|1|599",
        "Foundation|Isaac Asimov|Science Fiction|1951|Gnome Press|255|A mathematician plans to shorten the dark age after a galactic empire falls.|2|350",
        "Neuromancer|William Gibson|Science Fiction|1984|Ace|271|A washed-up hacker takes one last job in a neon-lit cyberspace.|1|425",
        "The Martian|Andy Weir|Science Fiction|2011|Crown|369|An astronaut stranded on Mars must science his way to survival.|3|450",
        "Ender's Game|Orson Scott Card|Science Fiction|1985|Tor|324|A gifted child is trained in a military school to fight an alien threat.|2|399",
        "Brave New World|Aldous Huxley|Science Fiction|1932|Chatto & Windus|311|A engineered, pleasure-managed society and the man who refuses it.|2|325",
        "The Hobbit|J. R. R. Tolkien|Fantasy|1937|Allen & Unwin|310|Bilbo Baggins is swept into a quest to reclaim a dwarf kingdom from a dragon.|2|499",
        "Harry Potter and the Philosopher's Stone|J. K. Rowling|Fantasy|1997|Bloomsbury|223|An orphan discovers he is a wizard and starts at Hogwarts.|4|450",
        "A Game of Thrones|George R. R. Martin|Fantasy|1996|Bantam|694|Noble houses scheme for the Iron Throne as winter approaches.|2|650",
        "The Name of the Wind|Patrick Rothfuss|Fantasy|2007|DAW|662|A legendary innkeeper tells the story of how he became a legend.|2|599",
        "A Wizard of Earthsea|Ursula K. Le Guin|Fantasy|1968|Parnassus Press|183|A young mage must face the shadow he unleashed.|1|325",
        "The Girl with the Dragon Tattoo|Stieg Larsson|Mystery & Thriller|2005|Norstedts|465|A journalist and a hacker investigate a decades-old disappearance.|2|499",
        "And Then There Were None|Agatha Christie|Mystery & Thriller|1939|Collins Crime Club|272|Ten strangers on an island are removed one by one.|3|299",
        "The Da Vinci Code|Dan Brown|Mystery & Thriller|2003|Doubleday|454|A symbologist races to decode a puzzle left in the Louvre.|2|399",
        "Gone Girl|Gillian Flynn|Mystery & Thriller|2012|Crown|422|On a couple's anniversary the wife vanishes and suspicion falls on her husband.|2|425",
        "The Hound of the Baskervilles|Arthur Conan Doyle|Mystery & Thriller|1902|George Newnes|256|Sherlock Holmes investigates a spectral hound on the moors.|2|250",
        "Sapiens|Yuval Noah Harari|History|2011|Harvill Secker|443|A sweeping history of how humans came to dominate the planet.|2|550",
        "Guns, Germs, and Steel|Jared Diamond|History|1997|W. W. Norton|425|Why some societies came to dominate others over 13,000 years.|2|575",
        "A Brief History of Time|Stephen Hawking|Science & Tech|1988|Bantam|212|Black holes, the Big Bang and the nature of time for general readers.|3|399",
        "Cosmos|Carl Sagan|Science & Tech|1980|Random House|365|A tour of the universe and humanity's place in it.|2|499",
        "The Selfish Gene|Richard Dawkins|Science & Tech|1976|Oxford University Press|224|Evolution seen from the point of view of the gene.|2|425",
        "The Diary of a Young Girl|Anne Frank|Biography|1947|Contact Publishing|283|A teenager's diary written while hiding during the Second World War.|2|275",
        "Steve Jobs|Walter Isaacson|Biography|2011|Simon & Schuster|656|The authorised biography of Apple's co-founder.|2|599",
        "Long Walk to Freedom|Nelson Mandela|Biography|1994|Little, Brown|630|Mandela's autobiography, from rural childhood to the presidency.|2|525",
        "Wings of Fire|A. P. J. Abdul Kalam;Arun Tiwari|Biography|1999|Universities Press|180|The autobiography of India's missile scientist and eleventh President.|4|250",
        "Atomic Habits|James Clear|Self-Help|2018|Avery|320|Small changes, compounded, that build good habits and break bad ones.|2|499",
        "Thinking, Fast and Slow|Daniel Kahneman|Self-Help|2011|Farrar, Straus and Giroux|499|How intuition and deliberate reasoning shape our judgments.|2|550",
        "The 7 Habits of Highly Effective People|Stephen R. Covey|Self-Help|1989|Free Press|381|A principle-centred approach to personal and professional effectiveness.|2|399",
        "Clean Code|Robert C. Martin|Computer Science|2008|Prentice Hall|464|Practical advice on writing readable, maintainable software.|2|899",
        "Introduction to Algorithms|Thomas H. Cormen;Charles E. Leiserson;Ronald L. Rivest;Clifford Stein|Computer Science|1990|MIT Press|1312|The standard university text on algorithms and data structures.|3|1250",
        "Database System Concepts|Abraham Silberschatz;Henry F. Korth;S. Sudarshan|Computer Science|1986|McGraw-Hill|1376|A comprehensive textbook on relational design, SQL and transactions.|4|1150",
        "Effective Java|Joshua Bloch|Computer Science|2001|Addison-Wesley|416|Programming-language guide with items on idiomatic Java.|2|799",
        "Design Patterns|Erich Gamma;Richard Helm;Ralph Johnson;John Vlissides|Computer Science|1994|Addison-Wesley|395|Twenty-three reusable object-oriented design patterns.|2|850",
        "The Pragmatic Programmer|Andrew Hunt;David Thomas|Computer Science|1999|Addison-Wesley|352|Habits and techniques for working programmers.|2|799",
        "Operating System Concepts|Abraham Silberschatz;Peter B. Galvin;Greg Gagne|Computer Science|1982|Wiley|944|Processes, memory, file systems and security in modern operating systems.|3|1100",
        "Computer Networking: A Top-Down Approach|James F. Kurose;Keith W. Ross|Computer Science|2000|Pearson|864|Networking taught from the application layer down to the wire.|2|1050",
        "Leaves of Grass|Walt Whitman|Poetry|1855|Self-published|145|The landmark collection celebrating the self, nature and democracy.|1|275",
        "Gitanjali|Rabindranath Tagore|Poetry|1910|India Society|104|Devotional poems from the first non-European Nobel laureate in literature.|2|199",
    };

    private static final String[] CATEGORY_COLORS = {
        "Fiction|#c8694d", "Classics|#b08d4a", "Science Fiction|#4f86c6", "Fantasy|#8a68c2", "Mystery & Thriller|#6b7f8c",
        "History|#98763f", "Science & Tech|#3f9c96", "Biography|#c25b84", "Self-Help|#6fa35a", "Computer Science|#4577c4", "Poetry|#cc8f5e"};

    private static final String[] MEMBERS = {
        "Aarav Menon|Student", "Diya Krishnan|Student", "Rohan Iyer|Student", "Meera Nair|Premium", "Karthik Raja|Student",
        "Ananya Sharma|Student", "Vikram Singh|Faculty", "Sneha Patel|Student", "Arjun Reddy|Student", "Kavya Subramanian|Premium",
        "Ishaan Gupta|Student", "Lakshmi Narayanan|Faculty", "Farhan Sheikh|Student", "Priya Das|Student"};

    private static final String[][] REVIEW_TEXT = {
        {},
        {"Didn't enjoy it."},
        {"Struggled to finish; not for me."},
        {"Decent, though it didn't fully land for me.", "Interesting ideas, uneven pacing."},
        {"Strong start and a satisfying ending.", "Dense in places but worth the effort.", "Great read; I'd borrow it again.", "Very good, a little slow in the middle."},
        {"Couldn't put it down; finished it in two sittings.", "A classic that earns its reputation.", "Clear, memorable and genuinely useful.", "Beautifully written. Recommend to everyone."}};

    static void run() throws Exception {
        final Random rnd = new Random(42);
        Db.tx(cn -> {
            final LocalDateTime now = LocalDateTime.now();

            // ---- plans
            Db.update(cn, "INSERT INTO plans (name, max_loans, loan_days, max_renewals, fine_per_day, annual_fee) VALUES "
                    + "('Student',5,14,2,2.00,0),('Faculty',10,30,3,1.00,0),('Premium',15,21,3,1.50,500)");
            long student = Db.num(cn, "SELECT id FROM plans WHERE name='Student'");
            Map<String, Long> planIds = new HashMap<>();
            for (Map<String, Object> p : Db.rows(cn, "SELECT id, name FROM plans")) planIds.put((String) p.get("name"), L(p.get("id")));

            // ---- staff + members
            long admin = Api.createUser(cn, "Anand Kumar", "admin@library.test", DEMO_PASSWORD, "ADMIN", planIds.get("Faculty"), null);
            long librarian = Api.createUser(cn, "Nisha Rao", "librarian@library.test", DEMO_PASSWORD, "LIBRARIAN", planIds.get("Faculty"), null);
            List<Long> members = new ArrayList<>();
            for (String m : MEMBERS) {
                String[] p = m.split("\\|");
                String email = p[0].toLowerCase().replace(' ', '.') + "@students.test";
                members.add(Api.createUser(cn, p[0], email, DEMO_PASSWORD, "MEMBER", planIds.get(p[1]), "9" + (100000000 + rnd.nextInt(899999999))));
            }
            // one member whose card has lapsed, to show the expiry rule
            long lapsed = Api.createUser(cn, "Tara Joseph", "tara.joseph@students.test", DEMO_PASSWORD, "MEMBER", student, null);
            Db.update(cn, "UPDATE users SET joined_on = ?, expires_on = ? WHERE id = ?", LocalDate.now().minusYears(2), LocalDate.now().minusDays(20), lapsed);

            // ---- categories, books, copies
            Map<String, Long> catIds = new HashMap<>();
            int ci = 0;
            for (String cc : CATEGORY_COLORS) {
                String[] p = cc.split("\\|");
                catIds.put(p[0], Db.insert(cn, "INSERT INTO categories (name, color) VALUES (?,?)", p[0], p[1]));
            }
            Map<String, Long> bookByTitle = new LinkedHashMap<>();
            int n = 0;
            for (String row : BOOKS) {
                String[] f = row.split("\\|");
                long id = Db.insert(cn, "INSERT INTO books (isbn, title, publisher, published_year, category_id, language, pages, description, added_on) VALUES (?,?,?,?,?,?,?,?,?)",
                        isbn13(++n), f[0], f[4], Integer.parseInt(f[3]), catIds.get(f[2]), "English", Integer.parseInt(f[5]), f[6], LocalDate.now().minusDays(200 - n));
                for (String a : f[1].split(";")) {
                    Db.update(cn, "INSERT IGNORE INTO authors (name) VALUES (?)", a.trim());
                    Db.update(cn, "INSERT IGNORE INTO book_authors (book_id, author_id) VALUES (?, (SELECT id FROM authors WHERE name = ?))", id, a.trim());
                }
                String shelf = (char) ('A' + new ArrayList<>(catIds.keySet()).indexOf(f[2]) % 26) + "-" + (1 + rnd.nextInt(12));
                int copies = Integer.parseInt(f[7]);
                // the four "always borrowed" titles are kept scarce so the holds queue has something to show
                if (Arrays.asList("Dune", "Atomic Habits", "Sapiens", "The Hobbit").contains(f[0])) copies = f[0].equals("Dune") || f[0].equals("Atomic Habits") ? 1 : 2;
                if (f[0].equals("Clean Code")) copies = 2;
                Api.addCopies(cn, id, copies, shelf, Double.parseDouble(f[8]));
                bookByTitle.put(f[0], id);
            }

            // ---- six months of returned loans, with the fines they produced
            List<Map<String, Object>> memberRows = Db.rows(cn, "SELECT u.id, p.loan_days, p.fine_per_day FROM users u JOIN plans p ON p.id = u.plan_id WHERE u.role = 'MEMBER' AND u.id <> ?", lapsed);
            List<Long> bookOrder = new ArrayList<>(bookByTitle.values());
            Collections.shuffle(bookOrder, rnd);
            Map<Long, List<Map<String, Object>>> copiesOf = new HashMap<>();
            for (Map<String, Object> cp : Db.rows(cn, "SELECT id, book_id, price FROM copies"))
                copiesOf.computeIfAbsent(L(cp.get("book_id")), k -> new ArrayList<>()).add(cp);
            Set<String> borrowed = new HashSet<>();
            for (int i = 0; i < 260; i++) {
                Map<String, Object> m = memberRows.get(rnd.nextInt(memberRows.size()));
                long bookId = bookOrder.get((int) (Math.pow(rnd.nextDouble(), 1.7) * bookOrder.size()));
                List<Map<String, Object>> cps = copiesOf.get(bookId);
                Map<String, Object> cp = cps.get(rnd.nextInt(cps.size()));
                int days = 8 + rnd.nextInt(175), loanDays = (int) L(m.get("loan_days"));
                LocalDateTime issued = now.minusDays(days).withHour(9 + rnd.nextInt(8)).withMinute(rnd.nextInt(60)).withSecond(0).withNano(0);
                LocalDateTime due = Circulation.endOfDay(issued.toLocalDate().plusDays(loanDays));
                LocalDateTime returned = issued.plusDays(3 + rnd.nextInt(loanDays + 6)).plusHours(rnd.nextInt(5));
                if (returned.isAfter(now.minusHours(3))) returned = now.minusDays(1);
                if (!returned.isAfter(issued)) continue;
                long loanId = Db.insert(cn, "INSERT INTO loans (copy_id, user_id, issued_at, due_at, returned_at, renewals, issued_by) VALUES (?,?,?,?,?,?,?)",
                        cp.get("id"), m.get("id"), issued, due, returned, rnd.nextInt(5) == 0 ? 1 : 0, rnd.nextBoolean() ? librarian : admin);
                borrowed.add(m.get("id") + ":" + bookId);
                double price = Circulation.D(cp.get("price"));
                double fine = Circulation.fineFor(due, returned, Circulation.D(m.get("fine_per_day")), price > 0 ? price : 500);
                if (fine > 0) {
                    int roll = rnd.nextInt(100);
                    String status = roll < 84 ? "PAID" : roll < 92 ? "WAIVED" : "UNPAID";
                    Db.update(cn, "INSERT INTO fines (loan_id, user_id, amount, reason, status, created_at, paid_at, method) VALUES (?,?,?,?,?,?,?,?)",
                            loanId, m.get("id"), fine, "OVERDUE", status, returned,
                            "UNPAID".equals(status) ? null : returned.plusDays(rnd.nextInt(3)),
                            "PAID".equals(status) ? (rnd.nextBoolean() ? "CASH" : "UPI") : "UNPAID".equals(status) ? null : "WAIVED");
                }
                if (rnd.nextInt(100) < 38) {
                    int stars = rnd.nextInt(100) < 36 ? 5 : rnd.nextInt(100) < 55 ? 4 : rnd.nextInt(100) < 55 ? 3 : rnd.nextInt(100) < 70 ? 2 : 1;
                    String[] pool = REVIEW_TEXT[stars];
                    Db.update(cn, "INSERT IGNORE INTO reviews (book_id, user_id, rating, body, created_at) VALUES (?,?,?,?,?)",
                            bookId, m.get("id"), stars, rnd.nextInt(4) == 0 ? null : pool[rnd.nextInt(pool.length)], returned.plusDays(1));
                }
            }

            // ---- today's circulation: ordinary active loans, a few overdue ones, and sold-out titles
            Set<Long> usedCopies = new HashSet<>();
            Map<Long, Integer> perUser = new HashMap<>();
            String[] hot = {"Dune", "Atomic Habits", "Sapiens", "The Hobbit"};
            int mi = 0;
            for (String t : hot) {
                for (Map<String, Object> cp : copiesOf.get(bookByTitle.get(t))) {
                    activeLoan(cn, rnd, now, memberRows, mi++, cp, bookByTitle.get(t), false, librarian, usedCopies, perUser, borrowed);
                }
            }
            List<Map<String, Object>> pool = new ArrayList<>();
            for (Map.Entry<Long, List<Map<String, Object>>> e : copiesOf.entrySet()) pool.addAll(e.getValue());
            Collections.shuffle(pool, rnd);
            int made = 0;
            for (Map<String, Object> cp : pool) {
                if (made >= 22) break;
                if (usedCopies.contains(L(cp.get("id")))) continue;
                long bookId = L(cp.get("book_id"));
                if (bookId == bookByTitle.get("Clean Code")) continue;
                if (activeLoan(cn, rnd, now, memberRows, mi, cp, bookId, made % 5 == 0, admin, usedCopies, perUser, borrowed)) { made++; mi++; }
            }

            // ---- holds: a waiting queue on the sold-out titles
            for (int k = 0; k < hot.length; k++) {
                long bid = bookByTitle.get(hot[k]);
                for (int w = 0; w < (k < 2 ? 2 : 1); w++) {
                    long uid = L(memberRows.get((k * 3 + w + 7) % memberRows.size()).get("id"));
                    if (Db.num(cn, "SELECT COUNT(*) FROM loans l JOIN copies c ON c.id = l.copy_id WHERE l.user_id = ? AND c.book_id = ? AND l.returned_at IS NULL", uid, bid) > 0) continue;
                    if (Db.num(cn, "SELECT COUNT(*) FROM reservations WHERE user_id = ? AND book_id = ?", uid, bid) > 0) continue;
                    Db.insert(cn, "INSERT INTO reservations (book_id, user_id, created_at) VALUES (?,?,?)", bid, uid, now.minusDays(2 - w).minusHours(rnd.nextInt(10)));
                }
            }
            // a hold that becomes READY through the real return path
            long cleanCode = bookByTitle.get("Clean Code");
            List<Map<String, Object>> cc = copiesOf.get(cleanCode);
            long u1 = L(memberRows.get(1).get("id")), u2 = L(memberRows.get(2).get("id")), u3 = L(memberRows.get(3).get("id"));
            activeLoan(cn, rnd, now, Arrays.asList(memberRows.get(1)), 0, cc.get(0), cleanCode, false, librarian, usedCopies, perUser, borrowed);
            activeLoan(cn, rnd, now, Arrays.asList(memberRows.get(2)), 0, cc.get(1), cleanCode, false, librarian, usedCopies, perUser, borrowed);
            Db.insert(cn, "INSERT INTO reservations (book_id, user_id, created_at) VALUES (?,?,?)", cleanCode, u3, now.minusDays(1));
            Map<String, Object> bc = Db.one(cn, "SELECT barcode FROM copies WHERE id = ?", cc.get(0).get("id"));
            Circulation.giveBack(cn, librarian, (String) bc.get("barcode"), "OK");

            // ---- favourites, requests
            for (long uid : members) {
                for (int k = 0; k < 2 + rnd.nextInt(4); k++)
                    Db.update(cn, "INSERT IGNORE INTO favorites (user_id, book_id) VALUES (?,?)", uid, bookOrder.get(rnd.nextInt(bookOrder.size())));
            }
            String[][] reqs = {
                {"The Psychology of Money", "Morgan Housel", "Would suit the finance club reading list.", "PENDING"},
                {"Deep Work", "Cal Newport", "Popular with final-year students before exams.", "PENDING"},
                {"Algorithms to Live By", "Brian Christian", "Good bridge between CS and everyday decisions.", "APPROVED"},
                {"Let Us C", "Yashavant Kanetkar", "First-year programming course text.", "PURCHASED"},
                {"Ikigai", "Hector Garcia; Francesc Miralles", "", "REJECTED"}};
            for (int k = 0; k < reqs.length; k++)
                Db.update(cn, "INSERT INTO book_requests (user_id, title, author, note, status, created_at, decided_by) VALUES (?,?,?,?,?,?,?)",
                        members.get(k + 4), reqs[k][0], reqs[k][1], reqs[k][2].isEmpty() ? null : reqs[k][2], reqs[k][3], now.minusDays(9 - k * 2),
                        "PENDING".equals(reqs[k][3]) ? null : librarian);

            Audit.log(admin, "SEED", "demo data: " + BOOKS.length + " titles, " + (members.size() + 3) + " accounts");
            return null;
        });
    }

    /** Direct insert of a current loan (bypasses the desk rules). Returns false if skipped. */
    private static boolean activeLoan(java.sql.Connection cn, Random rnd, LocalDateTime now, List<Map<String, Object>> memberRows, int idx,
                                      Map<String, Object> cp, long bookId, boolean overdue, long staff,
                                      Set<Long> used, Map<Long, Integer> perUser, Set<String> borrowed) throws Exception {
        Map<String, Object> m = memberRows.get(idx % memberRows.size());
        long uid = L(m.get("id"));
        if (perUser.getOrDefault(uid, 0) >= 3 || used.contains(L(cp.get("id")))) return false;
        if (Db.num(cn, "SELECT COUNT(*) FROM loans l JOIN copies c ON c.id = l.copy_id WHERE l.user_id = ? AND c.book_id = ? AND l.returned_at IS NULL", uid, bookId) > 0) return false;
        int loanDays = (int) L(m.get("loan_days"));
        int ago = overdue ? loanDays + 3 + rnd.nextInt(10) : 1 + rnd.nextInt(Math.max(2, loanDays - 3));
        LocalDateTime issued = now.minusDays(ago).withHour(10 + rnd.nextInt(6)).withMinute(rnd.nextInt(60)).withSecond(0).withNano(0);
        LocalDateTime due = Circulation.endOfDay(issued.toLocalDate().plusDays(loanDays));
        Db.insert(cn, "INSERT INTO loans (copy_id, user_id, issued_at, due_at, issued_by) VALUES (?,?,?,?,?)", cp.get("id"), uid, issued, due, staff);
        used.add(L(cp.get("id")));
        perUser.merge(uid, 1, Integer::sum);
        borrowed.add(uid + ":" + bookId);
        return true;
    }

    /** Valid ISBN-13 checksum on a made-up 978-9 prefix (sample data, not real ISBNs). */
    static String isbn13(int n) {
        String body = String.format("9789%08d", n * 7919);
        int sum = 0;
        for (int i = 0; i < 12; i++) sum += (body.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        return body + ((10 - sum % 10) % 10);
    }
}
